package com.cerner.healthe.direct.im.commands.filetransport;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.codec.binary.Hex;
import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.StanzaCollector;
import org.jivesoftware.smack.XMPPException.XMPPErrorException;
import org.jivesoftware.smack.packet.EmptyResultIQ;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.IQ.Type;
import org.jivesoftware.smackx.bytestreams.socks5.packet.Bytestream;
import org.jivesoftware.smackx.filetransfer.AuthSocks5Client;
import org.jivesoftware.smackx.filetransfer.FileTransferNegotiator;
import org.jivesoftware.smackx.hashes.HashManager;
import org.jivesoftware.smackx.hashes.element.HashElement;
import org.jivesoftware.smackx.jingle.JingleManager;
import org.jivesoftware.smackx.jingle.JingleSessionHandler;
import org.jivesoftware.smackx.jingle.JingleTransportMethodManager;
import org.jivesoftware.smackx.jingle.JingleUtil;
import org.jivesoftware.smackx.jingle.element.Jingle;
import org.jivesoftware.smackx.jingle.element.JingleAction;
import org.jivesoftware.smackx.jingle.element.JingleContent;
import org.jivesoftware.smackx.jingle.element.JingleContentDescription;
import org.jivesoftware.smackx.jingle.element.JingleContent.Creator;
import org.jivesoftware.smackx.jingle.transports.jingle_s5b.JingleS5BTransportManager;
import org.jivesoftware.smackx.jingle.transports.jingle_s5b.elements.JingleS5BTransport;
import org.jivesoftware.smackx.jingle.transports.jingle_s5b.elements.JingleS5BTransportCandidate;
import org.jivesoftware.smackx.jingle.transports.jingle_s5b.elements.JingleS5BTransportInfo;
import org.jivesoftware.smackx.jingle_filetransfer.element.JingleFileTransfer;
import org.jivesoftware.smackx.jingle_filetransfer.element.JingleFileTransferChild;
import org.jxmpp.jid.EntityFullJid;

import com.cerner.healthe.direct.im.packets.CredRequest;

/**
 * This class handles the operations of an outgoing file transfer.  The sender takes on the Jingle role
 * of the initiator and negotiates a SOCKS5 transfer with with the responder.  If SOCKS5 fails, the transfer
 * will fall back to an in-band (and slow) file transfer.
 * @author Greg Meyer
 * @since 1.0
 *
 */
public class OutgoingFileTransport implements JingleSessionHandler
{	
	protected static int PROXY_TYPE_PREFERENCE = 10;
	
	protected static ExecutorService negotiateTransferExecutor;

	protected static ExecutorService transferFileExecutor;
	
	protected final AbstractXMPPConnection con;
	
	protected final JingleS5BTransportManager jingleS5Manager;
	
	protected final JingleManager jingleManager;
	
	protected Map<String, Bytestream.StreamHost> candidateStreamhosts;
	
	protected final JingleUtil util;
	
	protected String dstAddressHashString;
	
	protected List<FileTransferDataListener> fileTransferDataListeners;
	
	protected TransportTypeMode transportMode;
	
	protected FileTransferSession ftSession;
	
	static
	{
		negotiateTransferExecutor = Executors.newSingleThreadExecutor();	
		
		transferFileExecutor = Executors.newSingleThreadExecutor();	
	}
	
	public OutgoingFileTransport(AbstractXMPPConnection con)
	{
		this.con = con;
		
		this.jingleS5Manager = (JingleS5BTransportManager)JingleTransportMethodManager.getTransportManager(con, JingleS5BTransport.NAMESPACE_V1);
		
		this.jingleManager = JingleManager.getInstanceFor(con);
		
		this.util = new JingleUtil(con);
		
		this.fileTransferDataListeners = new ArrayList<>();
		
		this.transportMode = TransportTypeMode.SOCKS5;
		
		this.ftSession = new FileTransferSession();
		
		ftSession.streamId = FileTransferNegotiator.getNextStreamID();
		
		ftSession.con = con;
		
		ftSession.sessionHandler = this;
	}

	public void addFileTransferDataListener(FileTransferDataListener listener)
	{
		fileTransferDataListeners.add(listener);
	}
	
	public void sendFile(EntityFullJid recipFullJid, File sendFile, String sendMessage) throws IOException
	{
		ftSession.sendFile = sendFile;
		ftSession.fileTransferTargetJID = recipFullJid;
		ftSession.fileName = sendFile.getName();
		
		final JingleUtil util = new JingleUtil(con);
		
		
		// Generate the list of candidate stream hosts
		List<Bytestream.StreamHost> streamHosts = null;
		try
		{
			streamHosts = jingleS5Manager.getAvailableStreamHosts();
		} 
		catch (XMPPErrorException | NotConnectedException | NoResponseException | InterruptedException e)
		{
			throw new IOException("Could not get stream host information for proxy server.", e);
		}
		
		if (streamHosts == null || streamHosts.size() == 0)
		{
			throw new IOException("Your TIM+ server is not advertising any proxy servers.");
		}
		
		/*
		 * The file details
		 */
		final FileDetails fileDetail = FileDetails.fileToDetails(sendFile);

		final JingleFileTransferChild jingleFile = new JingleFileTransferChild(Date.from(fileDetail.createdDtTm.toInstant()), 
				"", new HashElement(HashManager.ALGORITHM.SHA_256, fileDetail.hash), fileDetail.mimeType, fileDetail.name, fileDetail.size, null);
		
		/*
		 * The content description
		 */
		final JingleContentDescription desc = new JingleFileTransfer(Collections.singletonList(jingleFile));
		
		// Send the file offer and wait for an ACK
		try
		{
			/*
			 *  Generate a DST address for proxied connections
			 *  By spec of XEP 0065:
			 *     DST.ADDR = SHA1 Hash of: (SID + Requester JID + Target JID)
			 */
			final String dstAddress = ftSession.streamId + con.getUser().toString() + ftSession.fileTransferTargetJID.toString();
			final MessageDigest digest = MessageDigest.getInstance("SHA1");
			byte[] hash = digest.digest(dstAddress.getBytes(StandardCharsets.UTF_8));
			dstAddressHashString = Hex.encodeHexString(hash);
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new IOException("Failed to generate DST.ADDR for proxy transfer.", e);
		}
		

		final JingleS5BTransport.Builder builder = JingleS5BTransport.getBuilder();
		
		/*
		 * Add the candidates to the session offer
		 */
		candidateStreamhosts = new HashMap<>();
		for (Bytestream.StreamHost streamhost : streamHosts)
		{
			/*
			 *  By spec of XEP 0260, priority is calculated using the following formula
			 *  
			 *  (2^16)*(type preference) + (local preference)
			 *  Type Proxy = 10
			 *  
			 *  Pragmatically, all candidates will have the same preference unless
			 *  additional code is added to bost the local preference.
			 */
			int priority = (2^16)*(PROXY_TYPE_PREFERENCE) + getStreamhostLocalPreference(streamhost);
			final String candidateId = FileTransferNegotiator.getNextStreamID();
			
			candidateStreamhosts.put(candidateId, streamhost);
			
			final JingleS5BTransportCandidate candidate = new JingleS5BTransportCandidate(candidateId, streamhost.getAddress(), 
				streamhost.getJID(), 7777, priority, JingleS5BTransportCandidate.Type.proxy);
			builder.addTransportCandidate(candidate).setStreamId(ftSession.streamId).setMode(Bytestream.Mode.tcp).setDestinationAddress(dstAddressHashString).build();
		}
		
		final JingleS5BTransport transport = builder.build();
		
		/*
		 * The session-initiate request
		 */
		final Jingle fileOffer = util.createSessionInitiateFileOffer(recipFullJid, ftSession.streamId, JingleContent.Creator.initiator, 
				"file-offer", desc, transport);			
		
		// Send the file offer in a session-initiate message
		StanzaCollector fileOfferCollector;
		System.out.println("Sending file transfer session-initiate message.");
		try
		{
			fileOfferCollector = con.createStanzaCollectorAndSend(fileOffer);
		}
		catch (Exception e)
		{
			throw new IOException("Failed to send session-initiate message.", e);
		}
		
		try 
		{
			// Make sure the session-initiate was acknowledged
			final IQ result = fileOfferCollector.nextResultOrThrow();
			
			if (result != null && result instanceof EmptyResultIQ)
			{
				// setup the session handler
				jingleManager.registerJingleSessionHandler(recipFullJid, ftSession.streamId, this);
				
				System.out.println("File transfer session-initiate was acknowledged.  Waiting for the recipieint to accept the session");
			}
			else
				throw new IOException("The recipeint did not acknowledge the session-initiate request");
		}
		catch (NoResponseException | XMPPErrorException |
                InterruptedException | NotConnectedException e)
		{
			jingleManager.unregisterJingleSessionHandler(recipFullJid, ftSession.streamId, this);
			throw new IOException("Failed to receive an acknowledgment of the session-iniate request", e);
		}
	}

	@Override
	public IQ handleJingleSessionRequest(Jingle jingle)
	{
		// check if this is a session accept
		if (jingle.getAction() == JingleAction.session_accept)
		{	
			System.out.println("The recipient accepted the file transfer session.  Sending a session-accept acknowledgment");
			
			// by spec, we return an empty IQ result
			final EmptyResultIQ result = new EmptyResultIQ();
			result.setFrom(jingle.getTo());
			result.setTo(jingle.getFrom());
			result.setType(Type.result);
			result.setStanzaId(jingle.getStanzaId());
			
			return result;
		}
		else if (jingle.getAction() == JingleAction.session_terminate)
		{
			System.out.println("The recipient has terminated the session.");
			
			jingleManager.unregisterJingleSessionHandler(ftSession.fileTransferTargetJID, ftSession.streamId, OutgoingFileTransport.this);
			
			// by spec, we return an empty IQ result
			final EmptyResultIQ result = new EmptyResultIQ();
			result.setFrom(jingle.getTo());
			result.setTo(jingle.getFrom());
			result.setType(Type.result);
			result.setStanzaId(jingle.getStanzaId());
			
			return result;			
		}
		else if (jingle.getAction() == JingleAction.transport_info)
		{
			switch (TransportInfoType.getTransportInfoType(jingle.getContents().get(0)))
			{
				case CANDIDATE_USED:
				{
					final JingleS5BTransportInfo.CandidateUsed candidateUsed = 
							(JingleS5BTransportInfo.CandidateUsed)jingle.getContents().get(0).getTransport().getInfo();
					
					System.out.println("The recipient selected a proxy server with candidate ID " + candidateUsed.getCandidateId());
					
					ftSession.selectedCandidateId = candidateUsed.getCandidateId();
					
					// by spec, we return an empty IQ result
					final EmptyResultIQ result = new EmptyResultIQ();
					result.setFrom(jingle.getTo());
					result.setTo(jingle.getFrom());
					result.setType(Type.result);
					result.setStanzaId(jingle.getStanzaId());
					
					// start up the connection to our proxy server and manage the transfer
					negotiateTransferExecutor.execute(new Socks5ConnectionManager());
					
					return result;
				}
				case CANDIDATE_ACTIVATED:
				{
					/* no-op as the initator will send the activate command */
					break;
				}
				case CANDIATE_ERROR:
				{
					System.out.println("Recieved a candidate error from the responder.");
					if (this.transportMode == TransportTypeMode.SOCKS5)
					{
						System.out.println("Attempting to fall back to In-Band Bytestream mode");
					}
					
					break;
				}	
				case PROXY_ERROR:
				{
					System.out.println("Recieved a proxy-error from the responder.");
					if (this.transportMode == TransportTypeMode.SOCKS5)
					{
						System.out.println("Attempting to fall back to In-Band Bytestream mode");
					}
					break;
				}	
				case UNKNOWN:
					 default:
				{
					break;
				}
			}
		}
		
		return null;
	}
	
	protected class Socks5ConnectionManager implements Runnable
	{
		public Socks5ConnectionManager()
		{
			
		}
		
		@Override
		public void run()
		{
			// get the selected stream host using the selected CID
			final Bytestream.StreamHost selectedStreamhost = candidateStreamhosts.get(ftSession.selectedCandidateId);
					
			if (selectedStreamhost == null)
			{
				System.out.println("The recipient selected a proxy server not in the candidate list: aborting SOCKS5 transport.");
				
				// The candidate is not found in the candidate list
				// Send a proxy error	
				final Jingle proxyError = createTransportInfoMessage(JingleS5BTransportInfo.ProxyError.INSTANCE);
				con.sendIqRequestAsync(proxyError);

				return;
			}
			
			System.out.println("Attempting to connect to proxy server.");
			
			// connect to the stream host
			final AuthSocks5Client sock5Client = new AuthSocks5Client(selectedStreamhost, dstAddressHashString, con, ftSession.streamId, ftSession.fileTransferTargetJID);
			
			CredRequest creds = null;
			try
			{
				// get a one time user name password
				final CredRequest request = new CredRequest();
				request.setTo(selectedStreamhost.getJID());
				request.setType(IQ.Type.get);

				creds = (CredRequest)con.createStanzaCollectorAndSend(request).nextResultOrThrow();
			
			}
			catch (Exception e)
			{
				System.out.println("Failed to get one time proxy server username/password: aborting SOCKS5 transport.");
				
				// Can't get a user/pass
				// Send a proxy error	
				final Jingle proxyError = createTransportInfoMessage(JingleS5BTransportInfo.ProxyError.INSTANCE);
				con.sendIqRequestAsync(proxyError);
				
				return;
			}
			
			Socket socket = null;
			try
			{
				// creation of the socket connects and authenticates
				socket = sock5Client.getSocket(10000, creds.getSubject(), creds.getSecret(), creds.getProxyServerCA());
			}
			catch(Exception e)
			{
				// The candidate is not found in the candidate list
				// Send a proxy error	
				System.out.println("Failed to create socket connection.");
				
				
				final Jingle proxyError = createTransportInfoMessage(JingleS5BTransportInfo.ProxyError.INSTANCE);
				con.sendIqRequestAsync(proxyError);
			}
			
			try
			{
				// send the candidate used message
				final Jingle cadidateUsed = createTransportInfoMessage(new JingleS5BTransportInfo.CandidateUsed(ftSession.selectedCandidateId));
				
				System.out.println("Sending initiator candidiate used.");
				
				// send and wait for ack
				final IQ result = con.createStanzaCollectorAndSend(cadidateUsed).nextResultOrThrow();
				
				if (result != null && result instanceof EmptyResultIQ)
				{
					
					System.out.println("Activiating proxy");
					
					// activate the stream
					sock5Client.activate();
					
					final Jingle cadidateActiviate = createTransportInfoMessage(new JingleS5BTransportInfo.CandidateActivated(ftSession.selectedCandidateId));
					
					System.out.println("Sending candidate active.");
					
					con.sendIqRequestAsync(cadidateActiviate);
					
					ftSession.proxySocket = socket;
					
					// start sending the file on the file transfer thread
					transferFileExecutor.execute(new Socks5WriteManager(ftSession, fileTransferDataListeners));
				}
			}
			catch(Exception e)
			{
				System.out.println("Failed to active the session.");
				
				// Couldn't active the proxy stream
				// Send a proxy error	
				
				final Jingle proxyError = createTransportInfoMessage(JingleS5BTransportInfo.ProxyError.INSTANCE);
				con.sendIqRequestAsync(proxyError);
			}

		}
	}
	
	protected Jingle createTransportInfoMessage(JingleS5BTransportInfo info)
	{
		Jingle.Builder jb = Jingle.getBuilder();
		jb.setAction(JingleAction.transport_info)
		  .setInitiator(con.getUser())
		  .setSessionId(ftSession.streamId);
		
		final JingleS5BTransport transport = 
				JingleS5BTransport.getBuilder().setStreamId(ftSession.streamId).setTransportInfo(info).build();
		
		JingleContent.Builder cb = JingleContent.getBuilder();
		cb.setCreator(Creator.initiator)
		  .setName("file-offer")
		  .setTransport(transport);
		
		final Jingle jingle = jb.addJingleContent(cb.build()).build();
		jingle.setFrom(con.getUser());
		jingle.setTo(ftSession.fileTransferTargetJID);
		
		return jingle;
	}
	
	/**
	 * Allows for specific streamhosts to be assigned custom local preference. Defaults to value of 0
	 * if no special preference is found.
	 * @param streamHost The stream host to assign a custom preference to.
	 * @return The steamhosts local preference.
	 */
	public int getStreamhostLocalPreference(Bytestream.StreamHost streamHost)
	{
		/*
		 * default to 0; 
		 */
		return 0;
	}
}
