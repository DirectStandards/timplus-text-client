package org.directtruststandards.timplus.client;

import java.net.InetAddress;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.lang3.StringUtils;
import org.directtruststandards.timplus.client.commands.ChatCommands;
import org.directtruststandards.timplus.client.commands.FileTransferCommands;
import org.directtruststandards.timplus.client.commands.RoomChatCommands;
import org.directtruststandards.timplus.client.commands.RosterCommands;
import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration.Builder;
import org.jivesoftware.smackx.bytestreams.ibb.InBandBytestreamManager;
import org.jivesoftware.smackx.bytestreams.socks5.Socks5Proxy;
import org.jivesoftware.smackx.filetransfer.FileTransferNegotiator;
import org.nhindirect.common.tooling.Commands;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TIMPlusClientApplication implements CommandLineRunner
{

	@Value("${timplus.im.username}")
	protected String username;
	
	@Value("${timplus.im.password}")
	protected String password;
	
	@Value("${timplus.im.domain}")
	protected String domain;

	@Value("${timplus.im.hostip:}")
	protected String hostIp;
	
	@Value("${timplus.im.security.trustUnknownCAs:false}")
	protected boolean trustUnknownCAs;
	
	protected AbstractXMPPConnection con;
	
	protected XMPPTCPConnectionConfiguration xmppConfig;
	
	protected RosterCommands rosterCommands;
	
	protected ChatCommands chatCommands;
	
	protected FileTransferCommands fileTransferCommands;
	
	protected RoomChatCommands roomChatCommands;
	
	private static boolean exitOnEndCommands = true;
	
	public static void main(String[] args) 
	{
		SpringApplication.run(TIMPlusClientApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception
	{
		
		/*
		 * disable the local SOCKS5 proxy in favor of a server proxy (if we decided to use SOCKS5)
		 * local proxies are problematic when going across networks let alone organizational boundries
		 * Smack uses the IBB_ONLY field to indicate that only IBB should be use.  The use of SOCKS5 server proxies
		 * still imposes a security risk as they not encrypted by default.  
		 * NOTE: The use of IBB is suppose to be used only as a fallback, however it is common denominator that meets the 
		 * security requirements out of the box.  It is also VERY slow.
		 * TODO: Looking for an alternative XEP that performs better and meets security requirements.
		 */
		Socks5Proxy.setLocalSocks5ProxyEnabled(false);
		FileTransferNegotiator.IBB_ONLY = true;
		
		final Builder conBuilder = XMPPTCPConnectionConfiguration.builder().setUsernameAndPassword(username, password)
		.setXmppDomain(domain)
		.setCompressionEnabled(true);
		
		if (trustUnknownCAs)
		{
	        TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() 
	        {
	            public java.security.cert.X509Certificate[] getAcceptedIssuers() 
	            {
	                return null;
	            }
	            
	            public void checkClientTrusted(X509Certificate[] certs, String authType) 
	            {
	            }
	            
	            public void checkServerTrusted(X509Certificate[] certs, String authType) 
	            {
	            }
	        }};
	        
	        final SSLContext sc = SSLContext.getInstance("TLSv1.2");
	        sc.init(null, trustAllCerts, new java.security.SecureRandom());
	        
	        conBuilder.setCustomSSLContext(sc);
		}
		
		

		
		if (!StringUtils.isEmpty(hostIp))
			conBuilder.setHostAddress(InetAddress.getByName(hostIp));
		
		
		xmppConfig = conBuilder.build();
		
		connect();
		
		runApp();

		if (exitOnEndCommands)
		{
			Presence pres = new Presence(Presence.Type.unavailable);
			pres.setStatus("Not testing anymore");
			//pres.setStatus("Available");
			con.disconnect(pres);
			System.exit(0);		
		}
	}

	protected void connect() throws Exception
	{
		con = new XMPPTCPConnection(xmppConfig);
		con.addConnectionListener(new ConnectionListener()
		{
			@Override
			public void connected(XMPPConnection connection)
			{
				
			}

			@Override
			public void authenticated(XMPPConnection connection, boolean resumed)
			{
				
			}

			@Override
			public void connectionClosed()
			{
				
			}

			@Override
			public void connectionClosedOnError(Exception e)
			{
				/*
				 * for now just close and reconnect
				 */
				try
				{
					if (con != null && con.isConnected())
						con.disconnect();
				}
				catch (Exception conExp)
				{
					/* no op */
				}

				System.out.println("Connection was closed.  Reconnecting");
				try
				{
					connect();
					rosterCommands.init(con);
					chatCommands.init(con);
					fileTransferCommands.init(con);
					roomChatCommands.init(con);
				}
				catch(Exception conExp)
				{
					
				}
	
			}
			
			
		});
		
		System.out.println("Connecting to server for domain: " + domain);
		con.connect();
		
		System.out.println("Connection established.  Connecting as " + username + "@" + domain);
		con.login();
		
		Presence pres = new Presence(Presence.Type.available);
		pres.setStatus("Testing");
		con.sendStanza(pres);
		
		System.out.println("Login successful.  IM Client is running.");
		
		InBandBytestreamManager.getByteStreamManager(con).setDefaultBlockSize(16384);
		
	}
	
	
	public void runApp()
	{
		Commands commands = new Commands("Direct IM Client");

		rosterCommands = new RosterCommands(con);
		commands.register(rosterCommands);
		
		chatCommands = new ChatCommands(con);
		commands.register(chatCommands);
		
		fileTransferCommands = new FileTransferCommands(con);
		commands.register(fileTransferCommands);
		
		roomChatCommands = new RoomChatCommands(con);
		commands.register(roomChatCommands);
		
		
		commands.runInteractive();
		
	}
	
	/**
	 * Determines if the application should exit when command processing is complete.  It may be desirable to set this 
	 * to false if calling from another application context.  The default is true.
	 * @param exit True if the application should terminate on completing processing commands.  False otherwise.
	 */
	public static void setExitOnEndCommands(boolean exit)
	{
		exitOnEndCommands = exit;
	}
}
