package org.directtruststandards.timplus.client.commands.notifications;

import org.jivesoftware.smack.filter.FlexibleStanzaTypeFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smackx.amp.packet.AMPExtension;

public class AMPFilter extends FlexibleStanzaTypeFilter<Message>
{
    public AMPFilter() 
    {
        super(Message.class);
    }

    @Override
    protected boolean acceptSpecific(Message message) 
    {
    	return (message.getExtension(AMPExtension.NAMESPACE) != null);
    }
}
