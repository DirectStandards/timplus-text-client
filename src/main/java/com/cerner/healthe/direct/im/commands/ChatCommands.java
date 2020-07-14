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

public class ChatCommands
{
    private static final String SEND_MESSAGE = "Sends a message to a contact" +
    		"\r\n  contact message " +
            "\r\n\t contact: The username of the contact.  This is generally a full email address/Jabber id of the user." +
    		"\r\n\t message: The message to send to the contact.  This should eclose in a double quote (\" \") if the messge contains spaces.";
	
    protected AbstractXMPPConnection con;
	protected ChatManager chatManager;
    
    
    public ChatCommands(AbstractXMPPConnection con)
    {
    	init(con);
    }
    
    public void init(AbstractXMPPConnection con)
    {
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
}
