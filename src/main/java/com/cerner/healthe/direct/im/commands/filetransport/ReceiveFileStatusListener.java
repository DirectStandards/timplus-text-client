package com.cerner.healthe.direct.im.commands.filetransport;

public class ReceiveFileStatusListener implements FileTransferDataListener
{
	protected long totalBytesToReceive;
	
	protected int currentPercent;
	
	public ReceiveFileStatusListener(long totalBytesToReceive) 
	{
		this.totalBytesToReceive = totalBytesToReceive;
		
		currentPercent = 0;
	}

	@Override
	public int dataTransfered(long transferedSoFar)
	{
		double ratio = (double)transferedSoFar/(double)totalBytesToReceive;
		
		int percent =  (int) (ratio * 100);
		if (percent != currentPercent)
		{
			System.out.println("File transfer completion: " + percent + "%");
			currentPercent = percent;
		}
		
		
		return 0;
	}
}
