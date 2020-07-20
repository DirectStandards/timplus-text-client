package com.cerner.healthe.direct.im.commands;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.chat2.Chat;
import org.jivesoftware.smack.chat2.ChatManager;
import org.jivesoftware.smack.chat2.IncomingChatMessageListener;
import org.jivesoftware.smack.packet.Message;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.nhindirect.common.tooling.Command;
import org.nhindirect.common.tooling.StringArrayUtil;

import com.cerner.healthe.direct.im.commands.notifications.IncomingAMPMessageListener;
import com.cerner.healthe.direct.im.commands.notifications.MessageNotification;
import com.cerner.healthe.direct.im.commands.notifications.NotificationManager;

public class ChatCommands
{
    private static final String SEND_MESSAGE = "Sends a message to a contact" +
    		"\r\n  contact message " +
            "\r\n\t contact: The username of the contact.  This is generally a full email address/Jabber id of the user." +
    		"\r\n\t message: The message to send to the contact.  This should eclose in a double quote (\" \") if the messge contains spaces.";
	
    private static final String SHOW_NOTIFICATIONS = "Determins if message notification messages should be displayed.  This is turned off by default" +
    		"\r\n  show " +
            "\r\n\t show: Determines if notification messages should be displayed.  Set to true notificates should be displayed.  Any other value will set this to false.";
    
    protected AbstractXMPPConnection con;
	protected ChatManager chatManager;
    
    protected boolean showNotifications;
	
    public ChatCommands(AbstractXMPPConnection con)
    {
    	init(con);
    }
    
    public void init(AbstractXMPPConnection con)
    {
    	showNotifications = false;
    	
    	this.con = con;
    	
        chatManager = ChatManager.getInstanceFor(con);
        
        chatManager.addIncomingListener(new IncomingChatMessageListener() 
        {
     	   public void newIncomingMessage(EntityBareJid from, Message message, Chat chat) 
     	   {
     	      System.out.println("New message from " + from + ": " + message.getBody());
     	      System.out.println(">");
     	   }
        });
        
        NotificationManager.getInstanceFor(con).addIncomingAMPListener(new IncomingAMPMessageListener()
        {
        	public void newIncomingAMPMessage(EntityBareJid from, Message message, MessageNotification notif)
        	{
        		if (showNotifications)
        		{
	        		System.out.println("Message delivery notification:");
	        		System.out.println("\tStanza Id: " + notif.getStanzaId());
	        		System.out.println("\tTo: " + notif.getTo().toString());
	        		System.out.println("\tFrom: " + notif.getFrom().toString());
	        		System.out.println("\tDelivery Status: " + notif.getMessageStatus());
	        		System.out.println("\r\n>");
        		}
        	}
        });
    }
    
	@Command(name = "SendMessage", usage = SEND_MESSAGE)
	public void sendMessge(String[] args)
	{
		final String contact = StringArrayUtil.getRequiredValue(args, 0);
		final String message = StringArrayUtil.getRequiredValue(args, 1);
		
		try
		{			
			Chat chat = chatManager.chatWith(JidCreate.entityBareFrom(contact));
			chat.send(message);	
			
			System.out.println("Message sent\r\n");
		}
		catch (Exception e)
		{
			System.err.println("Error sending message: " + e.getMessage());
		}
	}
	
	@Command(name = "showNotifications", usage = SHOW_NOTIFICATIONS)
	public void showNotifications(String[] args)
	{
		final String showNotificationsStr = StringArrayUtil.getRequiredValue(args, 0);
		
		showNotifications = Boolean.parseBoolean(showNotificationsStr);
		
		System.out.println("ShowNotifications is now set to " + showNotifications);
	}
}
