package com.cerner.healthe.direct.im.commands.notifications;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jivesoftware.smack.Manager;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smackx.amp.AMPDeliverCondition;
import org.jivesoftware.smackx.amp.packet.AMPExtension;
import org.jivesoftware.smackx.amp.packet.AMPExtension.Rule;
import org.jxmpp.jid.Jid;

public class NotificationManager extends Manager
{
    private static final Map<XMPPConnection, NotificationManager> INSTANCES = new WeakHashMap<>();
	
    private final Set<IncomingAMPMessageListener> incomingAMPListeners = new CopyOnWriteArraySet<>();
    
    protected static ExecutorService notificateExecutor;
    
    public static synchronized NotificationManager getInstanceFor(XMPPConnection connection) 
    {
    	notificateExecutor = Executors.newSingleThreadExecutor();	
    	
    	NotificationManager chatManager = INSTANCES.get(connection);
        if (chatManager == null) 
        {
            chatManager = new NotificationManager(connection);
            INSTANCES.put(connection, chatManager);
        }
        return chatManager;
    }
    
    private NotificationManager(final XMPPConnection connection) 
    {
        super(connection);
        
        connection.addSyncStanzaListener(new StanzaListener() 
        {
            @Override
            public void processStanza(Stanza stanza) 
            {
            	final Message message = (Message)stanza;
            	
                final Jid from = message.getFrom();

                final AMPExtension ampExt = (AMPExtension)message.getExtension(AMPExtension.NAMESPACE);
                
                MessageNotification.MessageStatus status = null;
                
                for (Rule rule : ampExt.getRules())
                {
                	if (rule.getCondition() != null && rule.getCondition().getName() == AMPDeliverCondition.NAME)
                	{
                		final AMPDeliverCondition cond = AMPDeliverCondition.class.cast(rule.getCondition());
                		
                		if (cond.getValue().compareToIgnoreCase(AMPDeliverCondition.Value.direct.name()) == 0)
                		{
                			status = MessageNotification.MessageStatus.DELIVERED;
                		}
                		else if (cond.getValue().compareToIgnoreCase(AMPDeliverCondition.Value.stored.name()) == 0)
                		{
                			status = MessageNotification.MessageStatus.STORED_OFFLINE;
                		}
                	}
                }
                
                final MessageNotification notif = new MessageNotification(message.getStanzaId(), message.getTo().asBareJid(), 
                		from.asBareJid(), status);
                
                notificateExecutor.execute(new Runnable() 
                {
                	public void run()
                	{
		                for (IncomingAMPMessageListener listener : incomingAMPListeners) 
		                {
		                    listener.newIncomingAMPMessage(from.asEntityBareJidOrThrow(), message, notif);
		                }
                	}
                });
            }
        }, new AMPFilter());
    }
    
    public boolean addIncomingAMPListener(IncomingAMPMessageListener listener) 
    {
        return incomingAMPListeners.add(listener);
    }

    public boolean removeIncomingAMPListener(IncomingAMPMessageListener listener) 
    {
        return incomingAMPListeners.remove(listener);
    }
}
