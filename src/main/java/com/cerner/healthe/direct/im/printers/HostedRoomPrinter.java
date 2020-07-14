package com.cerner.healthe.direct.im.printers;

import java.util.ArrayList;
import java.util.Collection;

import org.jivesoftware.smackx.muc.HostedRoom;

public class HostedRoomPrinter extends AbstractRecordPrinter<HostedRoom>
{
	protected static final String ROOM_NAME = "Name";
	protected static final String ROOM_ID = "Jid";

	protected static final Collection<ReportColumn> REPORT_COLS;
	
	static
	{
		REPORT_COLS = new ArrayList<ReportColumn>();

		REPORT_COLS.add(new ReportColumn(ROOM_NAME, 50, "Name"));
		REPORT_COLS.add(new ReportColumn(ROOM_ID, 70, "Jid"));
	
	}
	
	public HostedRoomPrinter()
	{
		super(120, REPORT_COLS);
	}

}
