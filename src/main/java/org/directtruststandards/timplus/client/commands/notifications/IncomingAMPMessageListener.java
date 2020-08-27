package org.directtruststandards.timplus.client.commands.notifications;

import org.jivesoftware.smack.packet.Message;
import org.jxmpp.jid.EntityBareJid;

public interface IncomingAMPMessageListener
{
	public void newIncomingAMPMessage(EntityBareJid from, Message message, MessageNotification notif);
}
