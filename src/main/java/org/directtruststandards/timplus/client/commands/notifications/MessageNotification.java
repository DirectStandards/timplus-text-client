package org.directtruststandards.timplus.client.commands.notifications;

import org.jxmpp.jid.BareJid;

public class MessageNotification
{
	enum MessageStatus
	{
		DELIVERED,
		
		STORED_OFFLINE,
		
		ERROR
	}
	
	protected final String stanzaId;
	
	protected final BareJid to;
	
	protected final BareJid from;
	
	protected final MessageStatus messageStatus;
	
	public MessageNotification(String stanzaId, BareJid to, BareJid from, MessageStatus messageStatus)
	{
		this.stanzaId = stanzaId;
		this.to = to;
		this.from = from;
		this.messageStatus = messageStatus;
	}

	public String getStanzaId()
	{
		return stanzaId;
	}

	public BareJid getTo()
	{
		return to;
	}

	public BareJid getFrom()
	{
		return from;
	}

	public MessageStatus getMessageStatus()
	{
		return messageStatus;
	}
	
	
}
