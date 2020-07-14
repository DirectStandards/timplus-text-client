package com.cerner.healthe.direct.im.commands;

import java.util.Collection;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.roster.RosterListener;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.nhindirect.common.tooling.Command;
import org.nhindirect.common.tooling.StringArrayUtil;

import com.cerner.healthe.direct.im.printers.RosterPrinter;

public class RosterCommands
{
    private static final String LIST_CONTACTS = "Lists all contacts in the roster";
    
    private static final String ADD_CONTACT = "Adds a contact to the roster an requests authorization for communication" +
    		"\r\n  contact nickname " +
            "\r\n\t contact: The username of the contact.  This is generally a full email address/Jabber id of the user." +
    		"\r\n\t nickname: A nick name to give this contact.";
    
    private static final String REMOVE_CONTACT = "Removes a contact from the roster" +
    		"\r\n  contact nickname " +
            "\r\n\t contact: The username of the contact.  This is generally a full email address/Jabber id of the user.";
    
    protected AbstractXMPPConnection con;
    
    protected Roster roster;
    
    protected RosterPrinter rosterPrinter;
    
    public RosterCommands(AbstractXMPPConnection con)
    {
    	init(con);
    }
    
    public void init(AbstractXMPPConnection con)
    {
    	System.out.println("Loading roster");
    	
    	this.con = con;

    	this.roster = Roster.getInstanceFor(con);
    	
    	this.roster.setSubscriptionMode(Roster.SubscriptionMode.accept_all);
    	
    	this.rosterPrinter = new RosterPrinter(roster);

    	roster.addRosterListener(new RosterListener() 
    	{
    		public void entriesAdded(Collection<Jid> addresses) {}
    		public void entriesDeleted(Collection<Jid> addresses) {}
    		public void entriesUpdated(Collection<Jid> addresses) {}
    		public void presenceChanged(Presence presence) 
    		{
    			final Jid jid = presence.getFrom();
    			
    			System.out.println("Presence changed. From: " + jid.asBareJid() + " Status: " + RosterPrinter.getPresenceDisplay(presence));
    			System.out.println(">");
    		}
    	});
    }
    
	@Command(name = "ListContacts", usage = LIST_CONTACTS)
    public void listContacts(String[] args)
	{
		rosterPrinter.printRecords(roster.getEntries());
	}
	
	@Command(name = "AddContact", usage = ADD_CONTACT)
	public void addContact(String[] args)
	{
		final String contact = StringArrayUtil.getRequiredValue(args, 0);
		final String nickName = StringArrayUtil.getRequiredValue(args, 1);
		
		try
		{
			roster.createEntry(JidCreate.bareFrom(contact), nickName, null);
			System.out.println("Contact " + contact + " added");
		}
		catch (Exception e)
		{
			System.err.println("Failed to add contact: " + e.getMessage());
		}
	}
	
	@Command(name = "DeleteContact", usage = REMOVE_CONTACT)
	public void deleteContact(String[] args)
	{
		final String contact = StringArrayUtil.getRequiredValue(args, 0);
		
		for (RosterEntry entry : roster.getEntries())
		{
			if (entry.getJid().toString().compareToIgnoreCase(contact) == 0)
			{
				try
				{
					roster.removeEntry(entry);
					System.out.println("Contact " + contact + " deleted");
				}
				catch (Exception e)
				{
					System.err.println("Failed to delete contact: " + e.getMessage());
				}
				return;
			}
		}
		
		System.out.println("Contact " + contact + " not found in roster");
	}

}
