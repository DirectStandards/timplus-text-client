package com.cerner.healthe.direct.im.commands.filetransport;

import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.StanzaCollector;
import org.jivesoftware.smack.packet.EmptyResultIQ;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.IQ.Type;
import org.jivesoftware.smackx.bytestreams.socks5.packet.Bytestream;
import org.jivesoftware.smackx.bytestreams.socks5.packet.Bytestream.StreamHost;
import org.jivesoftware.smackx.filetransfer.AuthSocks5Client;
import org.jivesoftware.smackx.jingle.JingleHandler;
import org.jivesoftware.smackx.jingle.JingleManager;
import org.jivesoftware.smackx.jingle.JingleSessionHandler;
import org.jivesoftware.smackx.jingle.JingleUtil;
import org.jivesoftware.smackx.jingle.element.Jingle;
import org.jivesoftware.smackx.jingle.element.JingleAction;
import org.jivesoftware.smackx.jingle.element.JingleContent;
import org.jivesoftware.smackx.jingle.element.JingleContentTransport;
import org.jivesoftware.smackx.jingle.element.JingleContentTransportCandidate;
import org.jivesoftware.smackx.jingle.element.JingleReason;
import org.jivesoftware.smackx.jingle.element.JingleContent.Creator;
import org.jivesoftware.smackx.jingle.transports.jingle_s5b.elements.JingleS5BTransport;
import org.jivesoftware.smackx.jingle.transports.jingle_s5b.elements.JingleS5BTransportCandidate;
import org.jivesoftware.smackx.jingle.transports.jingle_s5b.elements.JingleS5BTransportInfo;
import org.jivesoftware.smackx.jingle_filetransfer.element.JingleFileTransfer;
import org.jxmpp.jid.EntityFullJid;

import com.cerner.healthe.direct.im.packets.CredRequest;

/**
 * This class handles incoming file transfer requests.
 * @author Greg Meyer
 * @since 1.0
 *
 */
public class IncomingFileTransport implements JingleHandler, JingleSessionHandler
{
	public final static String NAMESPACE = JingleFileTransfer.NAMESPACE_V5;
	
	protected FileTransferState transferState;
	
	protected AbstractXMPPConnection con;
	
	protected final JingleManager jingleManager;
	
	protected static ExecutorService acceptFileExecutor;
	
	protected String streamId;
	
	protected Bytestream.StreamHost selectedStreamhost;
	
	protected EntityFullJid fileTransferTargetJID;
	
	protected EntityFullJid initiatorJID;
	
	protected String selectedCandidateId; 
	
	static
	{
		acceptFileExecutor = Executors.newSingleThreadExecutor();	
	}
	
	public IncomingFileTransport(AbstractXMPPConnection con)
	{
		this.con = con;
		
		this.transferState = FileTransferState.SESSION_UNKNOWN;
		
		this.jingleManager = JingleManager.getInstanceFor(con);
	}
	
	/**
	 * Handles the session init request.
	 */
	@Override
	public IQ handleJingleRequest(Jingle jingle)
	{
		// check if this is a session initiate
		if (jingle.getAction() == JingleAction.session_initiate)
		{
			transferState = FileTransferState.SESSION_UNKNOWN;
			
			// by spec, we return an empty IQ result
			final EmptyResultIQ result = new EmptyResultIQ();
			result.setFrom(jingle.getTo());
			result.setTo(jingle.getFrom());
			result.setType(Type.result);
			result.setStanzaId(jingle.getStanzaId());
			
			transferState = FileTransferState.SESSION_INITIATE_ACK;
			
			acceptFileExecutor.execute(new Runnable() 
			{
				public void run()
				{
					JingleUtil util = new JingleUtil(con);
					try
					{
						System.out.println("Contact " + jingle.getFrom().asBareJid().toString() + " requesting to send file.");
						System.out.println("Auto accepting exchange in 1 second.");	
						
						Thread.sleep(1000);
						
						final Jingle sessionAccept = util.createSessionAccept(jingle.getFrom().asFullJidIfPossible(), jingle.getSid(), JingleContent.Creator.initiator, jingle.getContents().get(0).getName(), 
								JingleContent.Senders.initiator, jingle.getContents().get(0).getDescription(), jingle.getContents().get(0).getTransport());
						
						transferState = FileTransferState.SESSION_ACCEPT;
						
						jingleManager.registerJingleSessionHandler(jingle.getFrom().asFullJidIfPossible(), jingle.getSid(), IncomingFileTransport.this);
						
						final StanzaCollector fileOfferCollector = con.createStanzaCollectorAndSend(sessionAccept);
						
						// make sure we get an ack
						IQ result = fileOfferCollector.nextResultOrThrow();
						
						if (result != null && result instanceof EmptyResultIQ)
						{
							transferState = FileTransferState.SESSION_ACCEPT_ACK;
							System.out.println("Session accept was acked.");	
							
							// Set all of the class member variables and try
							// to connect to proxy servers
							streamId = jingle.getSid();
							fileTransferTargetJID = jingle.getTo().asEntityFullJidIfPossible();
							initiatorJID = jingle.getTo().asEntityFullJidIfPossible();
							
							final JingleS5BTransport transport = (JingleS5BTransport)jingle.getContents().get(0).getTransport();
							
							acceptFileExecutor.execute(new TargetSocks5ConnectionManager(transport.getCandidates(), transport.getDestinationAddress()));
						}
					
					}
					catch (Exception e)
					{
						transferState = FileTransferState.SESSION_TERIMINATE;
						
						jingleManager.unregisterJingleSessionHandler(jingle.getFrom().asFullJidIfPossible(), jingle.getSid(), IncomingFileTransport.this);
						
						final Jingle sessionTerm = util.createSessionTerminate(jingle.getFrom().asFullJidIfPossible(), jingle.getSid(), JingleReason.Timeout);
						con.sendIqRequestAsync(sessionTerm);
					}
				}
			});
			
			return result;
		}
		return null;
		
	}

	@Override
	public IQ handleJingleSessionRequest(Jingle jingle)
	{
		if (jingle.getAction() == JingleAction.transport_info)
		{
			switch (TransportInfoType.getTransportInfoType(jingle.getContents().get(0)))
			{
				case CANDIDATE_USED:
				{
					// by spec, we return an empty IQ result
					final EmptyResultIQ result = new EmptyResultIQ();
					result.setFrom(jingle.getTo());
					result.setTo(jingle.getFrom());
					result.setType(Type.result);
					result.setStanzaId(jingle.getStanzaId());
					
					return result;
				}
				case CANDIDATE_ACTIVATED:
				{
					// Time to get the transfer going
					
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

	protected class TargetSocks5ConnectionManager implements Runnable
	{
		protected List<JingleContentTransportCandidate> candidates;
		
		protected String dstAddressHashString;
		
		public TargetSocks5ConnectionManager(List<JingleContentTransportCandidate> candidates, String dstAddressHashString)
		{
			this.candidates = candidates;
		
		}
		
		@Override
		public void run()
		{
			final List<JingleContentTransportCandidate> sortedCandidates = new ArrayList<>(candidates);
			
			// sort by priority
			Collections.sort(sortedCandidates, new Comparator<JingleContentTransportCandidate>() 
			{
				  @Override
				  public int compare(JingleContentTransportCandidate c1, JingleContentTransportCandidate c2) 
				  {
					  if (c1 instanceof JingleS5BTransportCandidate && c2 instanceof JingleS5BTransportCandidate)
						  return ((JingleS5BTransportCandidate)c1).getPriority() > ((JingleS5BTransportCandidate)c1).getPriority() ? 1 : 0;
						  
					  return 0;
				  }
			});
			
			for (JingleContentTransportCandidate candidate : sortedCandidates)
			{
				final JingleS5BTransportCandidate s5can = (JingleS5BTransportCandidate)candidate;
				
				final StreamHost host = s5can.getStreamHost();
				
				// connect to the stream host
				final AuthSocks5Client sock5Client = new AuthSocks5Client(host, dstAddressHashString, con, streamId, fileTransferTargetJID);
				
				try
				{
					// get a one time user name password
					final CredRequest request = new CredRequest();
					request.setTo(host.getJID());
					request.setType(IQ.Type.get);

					final CredRequest creds = (CredRequest)con.createStanzaCollectorAndSend(request).nextResultOrThrow();
					
					// creation of the socket connects and authenticates
					final Socket socket = sock5Client.getSocket(10000, creds.getSubject(), creds.getSecret());
					
					selectedCandidateId = s5can.getCandidateId();
					
					// send the candidate used message
					final Jingle cadidateUsed = createTransportInfoMessage(new JingleS5BTransportInfo.CandidateUsed(selectedCandidateId));
					
					// make sure our peer acks the cadidate used message
					IQ result = con.createStanzaCollectorAndSend(cadidateUsed).nextResultOrThrow();
					
					if (result != null && result instanceof EmptyResultIQ)
					{
						// now sit and wait for the sender to send a candidate used message
					}
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
		}
	}
	
	
	protected Jingle createTransportInfoMessage(JingleS5BTransportInfo info)
	{
		Jingle.Builder jb = Jingle.getBuilder();
		jb.setAction(JingleAction.transport_info)
		  .setInitiator(initiatorJID)
		  .setSessionId(streamId);
		
		final JingleS5BTransport transport = 
				JingleS5BTransport.getBuilder().setStreamId(streamId).setTransportInfo(info).build();
		
		JingleContent.Builder cb = JingleContent.getBuilder();
		cb.setCreator(Creator.initiator)
		  .setName("file-offer")
		  .setTransport(transport);
		
		final Jingle jingle = jb.addJingleContent(cb.build()).build();
		jingle.setFrom(con.getUser());
		jingle.setTo(initiatorJID);
		
		return jingle;
	}
}
