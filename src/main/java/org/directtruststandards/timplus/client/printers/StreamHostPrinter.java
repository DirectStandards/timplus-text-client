package org.directtruststandards.timplus.client.printers;

import java.util.ArrayList;
import java.util.Collection;

import org.jivesoftware.smackx.bytestreams.socks5.packet.Bytestream.StreamHost;

public class StreamHostPrinter extends AbstractRecordPrinter<StreamHost>
{
	protected static final String HOST_JID = "Service Name";
	protected static final String HOST_ADDRESS = "Host Name";
	protected static final String HOST_PORT = "Port";
	
	protected static final Collection<ReportColumn> REPORT_COLS;
	
	static
	{
		REPORT_COLS = new ArrayList<ReportColumn>();

		REPORT_COLS.add(new ReportColumn(HOST_JID, 50, "JID"));
		REPORT_COLS.add(new ReportColumn(HOST_ADDRESS, 50, "Address"));
		REPORT_COLS.add(new ReportColumn(HOST_PORT, 10, "Port"));
	}
	
	public StreamHostPrinter()
	{
		super(110, REPORT_COLS);
	}
}
