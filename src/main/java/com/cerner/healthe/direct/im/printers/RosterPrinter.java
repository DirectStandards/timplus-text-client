package com.cerner.healthe.direct.im.printers;

import java.util.ArrayList;
import java.util.Collection;

import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jxmpp.jid.EntityFullJid;
import org.jxmpp.jid.Jid;

public class RosterPrinter extends AbstractRecordPrinter<RosterEntry>
{
	protected static final String ROSTER_ENTRY_NAME = "Name";
	protected static final String ROSTER_ENTRY_ID = "ID/JID";
	protected static final String ROSTER_FULL_JID = "Full JID";
	protected static final String ROSTER_APPROVED_STATUS = "Approved Status";
	protected static final String ROSTER_PRESENCE = "Presence";

	protected static final Collection<ReportColumn> REPORT_COLS;
	
	protected final Roster roster;
	
	static
	{
		REPORT_COLS = new ArrayList<ReportColumn>();

		REPORT_COLS.add(new ReportColumn(ROSTER_ENTRY_NAME, 50, "Name"));
		REPORT_COLS.add(new ReportColumn(ROSTER_ENTRY_ID, 50, "Jid"));
		REPORT_COLS.add(new ReportColumn(ROSTER_FULL_JID, 60, "Full Jid"));
		REPORT_COLS.add(new ReportColumn(ROSTER_APPROVED_STATUS, 30, "Approved"));		
		REPORT_COLS.add(new ReportColumn(ROSTER_PRESENCE, 30, "Presence"));		
	
	}
	
	public RosterPrinter(Roster roster)
	{
		super(240, REPORT_COLS);
		this.roster = roster;
	}
	
	@Override
	protected String getColumnValue(ReportColumn column, RosterEntry record)
	{
		
		try
		{
			if (column.header.equals(ROSTER_APPROVED_STATUS))
			{
				
				return record.canSeeHisPresence() ? "Approved" : (record.isSubscriptionPending() ? "Pending" : "Denied");
			}
			else if (column.header.equals(ROSTER_PRESENCE))
			{
				final Presence presense = roster.getPresence(record.getJid());
				return getPresenceDisplay(presense);

			}
			else if (column.header.equals(ROSTER_FULL_JID))
			{
				final Presence presense = roster.getPresence(record.getJid());
				final Jid jid = presense.getFrom();
				if (jid != null && jid instanceof EntityFullJid)
				{
					final EntityFullJid fullJid = (EntityFullJid)jid;
					return fullJid.toString();
				}

				return "** NA **";
			}
			
			return super.getColumnValue(column, record);
		}
		catch (Exception e)
		{
			return "ERROR: " + e.getMessage();
		}
	}	
	
	public static String getPresenceDisplay(Presence presense)
	{
		if (presense == null)
			return "Unknown";
		
		if (presense.getStatus() != null)
			return presense.getStatus();
		
		switch (presense.getType())
		{
			case available:
			{
				switch (presense.getMode())
				{
				   case available:
				   case chat:
					   return "Available";
				   case away:
					   return "Away";
				   case xa:
					   return "Away for extended time";
				   case dnd:
					   return "Do no disturb";
			       
				}
				
				return "Available";
			}
			default:
				return "Unavailable";
		}
	}
}
