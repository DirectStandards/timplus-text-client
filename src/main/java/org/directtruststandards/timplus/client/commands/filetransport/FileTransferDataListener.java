package org.directtruststandards.timplus.client.commands.filetransport;

public interface FileTransferDataListener
{	
	/**
	 * Call back when data is transfered.
	 * @param transferedSoFar The amount of data that has been transfered.
	 * @return Return 0 if the transfer can continue.  Anything else other than a 0 will interrupt and cancel the transfer.
	 */
	public int dataTransfered(long transferedSoFar);
}
