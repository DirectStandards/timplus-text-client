package com.cerner.healthe.direct.im.commands.filetransport;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.StanzaCollector;
import org.jivesoftware.smack.packet.EmptyResultIQ;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.IQ.Type;
import org.jivesoftware.smackx.jingle.JingleHandler;
import org.jivesoftware.smackx.jingle.JingleManager;
import org.jivesoftware.smackx.jingle.JingleSessionHandler;
import org.jivesoftware.smackx.jingle.JingleUtil;
import org.jivesoftware.smackx.jingle.element.Jingle;
import org.jivesoftware.smackx.jingle.element.JingleAction;
import org.jivesoftware.smackx.jingle.element.JingleContent;
import org.jivesoftware.smackx.jingle.element.JingleReason;
import org.jivesoftware.smackx.jingle_filetransfer.element.JingleFileTransfer;

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
						}
					
					}
					catch (Exception e)
					{
						transferState = FileTransferState.SESSION_TERIMINATE;
						
						jingleManager.unregisterJingleSessionHandler(jingle.getTo().asFullJidIfPossible(), jingle.getSid(), IncomingFileTransport.this);
						
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

	
	
}
