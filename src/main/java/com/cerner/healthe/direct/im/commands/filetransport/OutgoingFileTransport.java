package com.cerner.healthe.direct.im.commands.filetransport;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Date;
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
 * This class handles the operations of an outgoing file transfer.
 * @author Greg Meyer
 * @since 1.0
 *
 */
public class OutgoingFileTransport implements JingleSessionHandler
{
	public final static String NAMESPACE = JingleFileTransfer.NAMESPACE_V5;
	
	protected static ExecutorService sendFileExecutor;

	protected final AbstractXMPPConnection con;
	
	protected final JingleS5BTransportManager jingleS5Manager;
	
	protected final JingleManager jingleManager;
	
	protected FileTransferState transferState;
	
	protected String streamId;
	
	protected Bytestream.StreamHost streamhost;
	
	protected EntityFullJid recipFullJid;
	
	protected final JingleUtil util;
	
	protected String candidateId; 
	
	protected String dstAddressHashString;
	
	static
	{
		sendFileExecutor = Executors.newSingleThreadExecutor();	
	}
	
	public OutgoingFileTransport(AbstractXMPPConnection con)
	{
		this.con = con;
		
		this.transferState = FileTransferState.SESSION_UNKNOWN;
		
		this.jingleS5Manager = (JingleS5BTransportManager)JingleTransportMethodManager.getTransportManager(con, JingleS5BTransport.NAMESPACE_V1);
		
		this.streamId = FileTransferNegotiator.getNextStreamID();
		
		this.jingleManager = JingleManager.getInstanceFor(con);
		
		this.util = new JingleUtil(con);
	}

	public void sendFile(EntityFullJid recipFullJid, File sendFile, String sendMessage) throws IOException
	{
		this.recipFullJid = recipFullJid;
		
		final JingleUtil util = new JingleUtil(con);
		
		try
		{
			streamhost = jingleS5Manager.getAvailableStreamHosts().get(0);
		} 
		catch (XMPPErrorException | NotConnectedException | NoResponseException | InterruptedException e)
		{
			throw new IOException("Could not get stream host information for proxy server.", e);
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
		@SuppressWarnings("unchecked")
		final JingleContentDescription desc = new JingleFileTransfer(Collections.singletonList(jingleFile));
		
		/*
		 * The content transport proxy candidate
		 */
		candidateId = FileTransferNegotiator.getNextStreamID();
		final JingleS5BTransportCandidate candidate = new JingleS5BTransportCandidate(candidateId, streamhost.getAddress(), 
				streamhost.getJID(), 7777, 1, JingleS5BTransportCandidate.Type.proxy);
		
	
		// Send the file offer and wait for an ACK
		this.transferState = FileTransferState.SESSION_INITIATE;
		try
		{
			String dstAddress = this.streamId + con.getUser().toString() + this.recipFullJid.toString();
			MessageDigest digest = MessageDigest.getInstance("SHA1");
			byte[] hash = digest.digest(dstAddress.getBytes(StandardCharsets.UTF_8));
			dstAddressHashString = Hex.encodeHexString(hash);
			
			JingleS5BTransport.Builder builder = JingleS5BTransport.getBuilder();
			builder.addTransportCandidate(candidate).setStreamId(streamId).setMode(Bytestream.Mode.tcp).setDestinationAddress(dstAddressHashString).build();
			final JingleS5BTransport transport = builder.build();
			
			
			/*
			 * The session-initiate request
			 */
			Jingle fileOffer = util.createSessionInitiateFileOffer(recipFullJid, streamId, JingleContent.Creator.initiator, 
					"file-offer", desc, transport);			
			
			final StanzaCollector fileOfferCollector = con.createStanzaCollectorAndSend(fileOffer);
			
			// get the initial ack
			IQ result = fileOfferCollector.nextResultOrThrow();
			
			if (result != null && result instanceof EmptyResultIQ)
			{
				// setup the session handler
				jingleManager.registerJingleSessionHandler(recipFullJid, streamId, this);
				
				this.transferState = FileTransferState.SESSION_INITIATE_ACK;
				System.out.println("Session request was acked.");
				
			}
		}
		catch (Exception e)
		{
			jingleManager.unregisterJingleSessionHandler(recipFullJid, streamId, this);
			throw new IOException("Error sending file offer request.", e);
		}
	}

	@Override
	public IQ handleJingleSessionRequest(Jingle jingle)
	{
		// check if this is a session accept
		if (jingle.getAction() == JingleAction.session_accept)
		{
			this.transferState = FileTransferState.SESSION_ACCEPT;
			
			System.out.println("Acking session accept.");
			
			// by spec, we return an empty IQ result
			final EmptyResultIQ result = new EmptyResultIQ();
			result.setFrom(jingle.getTo());
			result.setTo(jingle.getFrom());
			result.setType(Type.result);
			result.setStanzaId(jingle.getStanzaId());
			
			this.transferState = FileTransferState.SESSION_ACCEPT_ACK;
			
			return result;
		}
		else if (jingle.getAction() == JingleAction.transport_info)
		{
			switch (TransportInfoType.getTransportInfoType(jingle.getContents().get(0)))
			{
				case CANDIDATE_USED:
				{
					System.out.println("Acking responder candidate used.");
					
					// by spec, we return an empty IQ result
					final EmptyResultIQ result = new EmptyResultIQ();
					result.setFrom(jingle.getTo());
					result.setTo(jingle.getFrom());
					result.setType(Type.result);
					result.setStanzaId(jingle.getStanzaId());
					
					// start up the connection to our proxy server and manage the transfer
					sendFileExecutor.execute(new Socks5ConnectionManager());
					
					return result;
				}
				case CANDIDATE_ACTIVATED:
				{
					break;
				}
				case CANDIATE_ERROR:
				{
					
					
					break;
				}	
				case PROXY_ERROR:
				{
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
			System.out.println("Attempting to connect to proxy server.");
			
			// connect to the stream host
			final AuthSocks5Client sock5Client = new AuthSocks5Client(streamhost, dstAddressHashString, con, streamId, recipFullJid);
			
			try
			{
				// get a one time user name password
				final CredRequest request = new CredRequest();
				request.setTo(streamhost.getJID());
				request.setType(IQ.Type.get);

				final CredRequest creds = (CredRequest)con.createStanzaCollectorAndSend(request).nextResultOrThrow();
				
				
				// creation of the socket connects and authenticates
				Socket socket = sock5Client.getSocket(10000, creds.getSubject(), creds.getSecret());
				
				// send the candidate used message
				final Jingle cadidateUsed = createTransportInfoMessage(new JingleS5BTransportInfo.CandidateUsed(candidateId));
				
				
				transferState = FileTransferState.INITIATOR_CANDIDATE_USED;
				
				System.out.println("Sending initiator candidiate used.");
				
				// send and wait for ack
				final IQ result = con.createStanzaCollectorAndSend(cadidateUsed).nextResultOrThrow();
				
				if (result != null && result instanceof EmptyResultIQ)
				{
					transferState = FileTransferState.INITIATOR_CANDIDATE_USED;
					
					System.out.println("Activiating proxy");
					
					// activate the stream
					sock5Client.activate();
					
					final Jingle cadidateActiviate = createTransportInfoMessage(new JingleS5BTransportInfo.CandidateActivated(candidateId));
					
					System.out.println("Sending candidate active.");
					
					con.sendIqRequestAsync(cadidateActiviate);
				}
				
				// start transferring data
				
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}
	
	protected Jingle createTransportInfoMessage(JingleS5BTransportInfo info)
	{
		Jingle.Builder jb = Jingle.getBuilder();
		jb.setAction(JingleAction.transport_info)
		  .setInitiator(con.getUser())
		  .setSessionId(streamId);
		
		final JingleS5BTransport transport = 
				JingleS5BTransport.getBuilder().setStreamId(streamId).setTransportInfo(info).build();
		
		JingleContent.Builder cb = JingleContent.getBuilder();
		cb.setCreator(Creator.initiator)
		  .setName("file-offer")
		  .setTransport(transport);
		
		final Jingle jingle = jb.addJingleContent(cb.build()).build();
		jingle.setFrom(con.getUser());
		jingle.setTo(recipFullJid);
		
		return jingle;
	}
}
