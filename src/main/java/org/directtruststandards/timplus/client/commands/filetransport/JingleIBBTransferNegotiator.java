package org.directtruststandards.timplus.client.commands.filetransport;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smackx.filetransfer.IBBTransferNegotiator;

public class JingleIBBTransferNegotiator extends IBBTransferNegotiator
{
	public JingleIBBTransferNegotiator(AbstractXMPPConnection con)
	{
		super(con);
	}
}
