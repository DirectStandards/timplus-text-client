package com.cerner.healthe.direct.im.commands;

import java.io.File;
import java.text.DecimalFormat;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smackx.bytestreams.socks5.packet.Bytestream;
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jivesoftware.smackx.disco.packet.DiscoverItems;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo.Identity;
import org.jivesoftware.smackx.filetransfer.FileTransfer;
import org.jivesoftware.smackx.filetransfer.FileTransfer.Status;
import org.jivesoftware.smackx.filetransfer.FileTransferListener;
import org.jivesoftware.smackx.filetransfer.FileTransferManager;
import org.jivesoftware.smackx.filetransfer.FileTransferRequest;
import org.jivesoftware.smackx.filetransfer.IncomingFileTransfer;
import org.jivesoftware.smackx.filetransfer.Socks5TransferNegotiator;
import org.jivesoftware.smackx.jingle.JingleManager;
import org.jivesoftware.smackx.jingle.JingleTransportMethodManager;
import org.jivesoftware.smackx.jingle.transports.jingle_s5b.JingleS5BTransportManager;
import org.jivesoftware.smackx.jingle.transports.jingle_s5b.elements.JingleS5BTransport;
import org.jivesoftware.smackx.jingle_filetransfer.element.JingleFileTransfer;
import org.jxmpp.jid.EntityFullJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.nhindirect.common.tooling.Command;
import org.nhindirect.common.tooling.StringArrayUtil;

import com.cerner.healthe.direct.im.commands.filetransport.FileDetails;
import com.cerner.healthe.direct.im.commands.filetransport.FileTransferDataListener;
import com.cerner.healthe.direct.im.commands.filetransport.IncomingFileTransport;
import com.cerner.healthe.direct.im.commands.filetransport.OutgoingFileTransport;
import com.cerner.healthe.direct.im.packets.CredRequest;
import com.cerner.healthe.direct.im.packets.CredRequestProvider;
import com.cerner.healthe.direct.im.printers.StreamHostPrinter;

public class FileTransferCommands
{
    private static final String SEND_FILE = "Sends a request to send a file." +
    		"\r\n  contact file (message) " +
            "\r\n\t contact: The username of the contact.  This is generally a full email address/Jabber id of the user." +
            "\r\n\t file: Full path of the file to send..  This should eclose in a double quote (\\\" \\\") if the file name/path contains spaces." +
    		"\r\n\t message: Optional.  A message to send to the contact.  This should eclose in a double quote (\" \") if the messge contains spaces.";
	
    private static final String ACCEPT_INCOMING_FILES = "Indicates if incoming file transfer requests should be accepted of rejected." +
    		"\r\n  accept " +
            "\r\n\t accept: Indicates if incoming requests should be accepted.  Set to true if requests should be accepted.  Any other value will set this to false"; 
    
    private static final String LIST_FT_PROXY_SETTING = "List the setting of the filetransfer proxy server configured for the optionally supplied domain." +
    		"\r\n  domain " +
            "\r\n\t domain: Optional. The domain to retrieve proxy setting.  If this paramater is ommitted, the currently connected domain will be used."; 
    
    private static final String CREATE_PROXY_CREDENTIAL = "Creates a one time credential for the proxy server." +
    		"\r\n  domain " +
            "\r\n\t domain: Optional. The domain to retrieve proxy setting.  If this paramater is ommitted, the currently connected domain will be used."; 
    
    protected static final String PROXY_SERVICE_PREFIX = "ftproxystream";
    
	protected AbstractXMPPConnection con;
	
	protected FileTransferManager transferManager;
	
	protected Roster roster;
	
	protected boolean acceptFiles = true;
	
	protected ServiceDiscoveryManager discoManager;
	
	protected StreamHostPrinter bytestreamPrinter;
	
	protected JingleS5BTransportManager jingleS5Manager;
	
	protected Socks5TransferNegotiator s5Manager;
	
	protected final JingleManager jingleManager;
	
	public FileTransferCommands(AbstractXMPPConnection con)
	{	
		init(con);
		
		bytestreamPrinter = new StreamHostPrinter();
		
		jingleManager = JingleManager.getInstanceFor(con);
		jingleManager.registerDescriptionHandler(JingleFileTransfer.NAMESPACE_V5, new IncomingFileTransport(con));
		jingleS5Manager = (JingleS5BTransportManager)JingleTransportMethodManager.getTransportManager(con, JingleS5BTransport.NAMESPACE_V1);
		
		ProviderManager.addIQProvider(CredRequest.ELEMENT, CredRequest.NAMESPACE, new CredRequestProvider());
	}
	
	public void init(AbstractXMPPConnection con)
	{
		this.con = con;
		
		roster = Roster.getInstanceFor(con);
		
		discoManager = ServiceDiscoveryManager.getInstanceFor(con);
		
		transferManager = FileTransferManager.getInstanceFor(con);
		
		transferManager.addFileTransferListener(new FileTransferListener() 
		{
			public void fileTransferRequest(FileTransferRequest request) 
			{
				try
				{
					handleFileTransferRequest(request);
				}
				catch (Exception e)
				{
					System.err.println("Error in incoming file transfer: " + e.getMessage());
				}
			}
		});
	}
	
	@Command(name = "SendFile", usage = SEND_FILE)
	public void sendFile(String[] args) throws Exception
	{
		final String contact = StringArrayUtil.getRequiredValue(args, 0);
		final String file = StringArrayUtil.getRequiredValue(args, 1);
		final String message = StringArrayUtil.getOptionalValue(args, 2, "");
		
		final Presence presense = roster.getPresence(JidCreate.entityBareFrom(contact));
		final Jid jid = presense.getFrom();
		if (jid == null || !(jid instanceof EntityFullJid))
		{
			System.out.println("No resource found for contact.  Contact may not be online");
			return;
		}
		
		final EntityFullJid fullJid = (EntityFullJid)jid;

		File sendFile = new File(file);
		final FileDetails fileDetail = FileDetails.fileToDetails(sendFile);
		
		
		OutgoingFileTransport outTransport = new OutgoingFileTransport(con);
		outTransport.addFileTransferDataListener(new SendFileStatusListener(fileDetail.getSize()));
		outTransport.sendFile(fullJid, new File(file), message);
		
		try
		{		
			/*
			OutputStream outStream = this.s5Manager.createOutgoingStream(FileTransferNegotiator.getNextStreamID(), con.getUser(), fullJid);
			
			
			final OutgoingFileTransfer transfer = transferManager.createOutgoingFileTransfer(fullJid);
			//Send the file
			transfer.sendFile(new File(file), message);
			
			System.out.println("File transfer request sent\r\n");
			
			sendFileMonitorExecutor.execute(new Runnable()
			{
				public void run()
				{
					try
					{
						monitorFileTransfer(transfer);
					}
					catch (Exception e)
					{
						System.err.println("Error in sending file " + transfer.getFileName() + ":" + e.getMessage());
					}
				}
			});
			*/
		}
		catch (Exception e)
		{
			System.err.println("Error sending file: " + e.getMessage());
		}
	}

	
	@Command(name = "AcceptIncomingFiles", usage = ACCEPT_INCOMING_FILES)
	public void acceptIncomingFiles(String[] args) throws Exception
	{
		final String acceptString = StringArrayUtil.getRequiredValue(args, 0);
		
		acceptFiles = Boolean.parseBoolean(acceptString);
		
		System.out.println("Auto accept is now set to " + acceptFiles);
	}
	
	@Command(name = "ListFtProxySetting", usage = LIST_FT_PROXY_SETTING)
	public void listFtProxySetting(String[] args) throws Exception
	{		
		/*
		 * This is functionally identical to s5Manager.getAvailableStreamHosts() except
		 * this allows us to query other domains other than our currently connected domain.
		 * It also does not cache the results meaning that if a server it not initially
		 * available, we can still get the settings at a later time.
		 */
		String domain = StringArrayUtil.getOptionalValue(args, 0, "");
		
		if (StringUtils.isEmpty(domain))
			domain = con.getXMPPServiceDomain().toString();		
		
		final Bytestream proxySettings = getDomainFTProxySettings(domain);
		
		if (proxySettings != null)
		{
			System.out.println("Found proxy service address settings for domain " + domain + "\r\n");
			bytestreamPrinter.printRecords(proxySettings.getStreamHosts());
		}
		else
			System.out.println("Could not discovery address info for domain " + domain);
	}
	
	@SuppressWarnings("deprecation")
	@Command(name = "CreateProxyCredential", usage = CREATE_PROXY_CREDENTIAL)
	public void createProxyCredential(String[] args) throws Exception
	{
		String domain = StringArrayUtil.getOptionalValue(args, 0, "");
		
		if (StringUtils.isEmpty(domain))
			domain = con.getXMPPServiceDomain().toString();
		
		final CredRequest request = new CredRequest();
		request.setTo(PROXY_SERVICE_PREFIX + "." + domain);
		request.setType(IQ.Type.get);

		final CredRequest creds = (CredRequest)con.createStanzaCollectorAndSend(request).nextResultOrThrow();
		
		System.out.println("Credentials: ");
		System.out.println("\tSubject: " + creds.getSubject());
		System.out.println("\tSecret: " + creds.getSecret());
		final String caSubject = (creds.getProxyServerCA() == null) ? "<None>" : creds.getProxyServerCA().getSubjectDN().toString();
		System.out.println("\tProxy server CA Subject: " + caSubject);
	}
	
	protected synchronized void handleFileTransferRequest(FileTransferRequest request) throws Exception
	{
		System.out.println("Request from " + request.getRequestor() + " to send file.");
		System.out.println("\tFile Name: " + request.getFileName());
		System.out.println("\tFile Size: " + request.getFileSize() + " bytes");
		
		if (!acceptFiles)
		{
			request.reject();
			System.out.println("\r\nFile transfer request rejected");
			return;
		}

		System.out.println("\r\nAccepting file transfer.");
		final IncomingFileTransfer transfer = request.accept();
		transfer.receiveFile(new File(request.getFileName()));
		

		monitorFileTransfer(transfer);
	}
	
	protected void monitorFileTransfer(FileTransfer transfer) throws Exception
	{
		Status currentStatus = null;
		while(!transfer.isDone()) 
		{		

			if (transfer.getStatus() != currentStatus)
			{
				currentStatus = transfer.getStatus();
				System.out.println("Transfer status: " + transfer.getStatus());
			}
			if (transfer.getStatus().equals(Status.in_progress))
			{
				final DecimalFormat df = new DecimalFormat("#.#");
				
				String percent = df.format((transfer.getProgress() * 100.0d));
				System.out.println("Transfer progress " + percent + "%");
			}

			Thread.sleep(500);
		}
		
		System.out.println("Final transfer status: " + transfer.getStatus());
		if (transfer.getStatus().equals(Status.error)) 
		{
			System.out.println("Transfer errored out with exception: " + transfer.getException().getMessage());
			transfer.getException().printStackTrace();
			return;
		} 	

	}
	
	protected Bytestream getDomainFTProxySettings(String domain) throws Exception
	{
		Bytestream retVal = null;
		
		// technically TIM+ requires that a server publish a proxy server,
		// but this is a sanity check to ensure that the connected server is
		// in compliance with the spec
		final DiscoverItems serverItems = discoManager.discoverItems(JidCreate.from(domain));
		
		DiscoverItems.Item proxyServiceItem = null;
		
		if (serverItems != null && !serverItems.getItems().isEmpty())
		{
			for (DiscoverItems.Item item : serverItems.getItems())
			{
				if (item.getEntityID().toString().startsWith(PROXY_SERVICE_PREFIX))
				{
					proxyServiceItem = item;
					break;
				}			
			}
			
			if (proxyServiceItem  != null)
			{
				// ensure the item actually supports the proxy service protocol
				final DiscoverInfo proxyServiceInfo = discoManager.discoverInfo(proxyServiceItem.getEntityID());
				if (proxyServiceInfo != null)
				{
					final List<Identity> identities = proxyServiceInfo.getIdentities("proxy", "bytestreams");
					if (identities != null && !identities.isEmpty())
					{
						final Bytestream request = new Bytestream();
						request.setTo(proxyServiceItem.getEntityID());
						// lastly, get the proxy addressing information
						retVal = (Bytestream)con.createStanzaCollectorAndSend(request).nextResultOrThrow();
						
					}
					else
						System.out.println("Discovered entity " + proxyServiceItem.getEntityID().toString() + " is not a proxy service.");
				}
				else
					System.out.println("No information discoverable for proxy service " + proxyServiceItem.getEntityID().toString());
			}
			else
				System.out.println("No proxy service found at domain " + domain);
			
		}
		else
			System.out.println("No items discovered at domain " + domain);		
		
		return retVal;
	}
	
	protected class SendFileStatusListener implements FileTransferDataListener
	{
		protected long totalBytesToSend;
		
		protected int currentPercent;
		
		public SendFileStatusListener(long totalBytesToSend) 
		{
			this.totalBytesToSend = totalBytesToSend;
			
			currentPercent = 0;
		}

		@Override
		public int dataTransfered(long transferedSoFar)
		{
			double ratio = (double)transferedSoFar/(double)totalBytesToSend;
			
			int percent =  (int) (ratio * 100);
			if (percent != currentPercent)
			{
				System.out.println("File transfer completion: " + percent + "%");
				currentPercent = percent;
			}
			
			
			return 0;
		}
	}
	
	protected class ReceiveFileStatusListener implements FileTransferDataListener
	{
		protected long totalBytesToReceive;
		
		protected int currentPercent;
		
		public ReceiveFileStatusListener(long totalBytesToReceive) 
		{
			this.totalBytesToReceive = totalBytesToReceive;
			
			currentPercent = 0;
		}

		@Override
		public int dataTransfered(long transferedSoFar)
		{
			double ratio = (double)transferedSoFar/(double)totalBytesToReceive;
			
			int percent =  (int) (ratio * 100);
			if (percent != currentPercent)
			{
				System.out.println("File transfer completion: " + percent + "%");
				currentPercent = percent;
			}
			
			
			return 0;
		}
	}
}
