package org.directtruststandards.timplus.client.commands.filetransport;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import org.jivesoftware.smackx.filetransfer.IBBTransferNegotiator;
import org.jivesoftware.smackx.jingle.JingleManager;
import org.jivesoftware.smackx.jingle.JingleUtil;
import org.jivesoftware.smackx.jingle.element.Jingle;

public class IBBWriteManager implements Runnable
{
	protected final FileTransferSession ftSession;
	
	protected final List<FileTransferDataListener> fileTransferDataListeners;
	
	protected final JingleManager jingleManager;
	
	protected final JingleUtil util;
	
	public IBBWriteManager(FileTransferSession ftSession, List<FileTransferDataListener> fileTransferDataListeners)
	{
		this.ftSession = ftSession;
		this.fileTransferDataListeners = fileTransferDataListeners;
		
		this.util = new JingleUtil(ftSession.con);
		
		this.jingleManager = JingleManager.getInstanceFor(ftSession.con);
	}
	
	@Override
	public void run()
	{
		System.out.println("Opening In-Band file transfer");
		
		final IBBTransferNegotiator inbandTransferManager = new JingleIBBTransferNegotiator(ftSession.con);
		
		try(OutputStream outStream = inbandTransferManager.createOutgoingStream(ftSession.streamId, ftSession.con.getUser(), ftSession.fileTransferTargetJID);
				final InputStream fileInstream = new BufferedInputStream(new FileInputStream(ftSession.sendFile)))
		{
			System.out.println("In-Band file stream is open.  Starting to send data.");
			
			// Allocate a 4K buffer
			byte[] buffer = new byte[4096];
			long writtenSoFar = 0;
			int read = fileInstream.read(buffer);
			writtenSoFar += read;
			while (read > -1)
			{
				outStream.write(buffer, 0, read);
				
				for (FileTransferDataListener listener : fileTransferDataListeners )
				{
					// don't let an exception in the listener kill the transport
					try 
					{
						if (listener.dataTransfered(writtenSoFar) != 0)
						{
							// the transfer was interrupted by the listener
							
							System.out.println("File transfer was interrupted by a transfer listener.  Terminating the session");
							
							// terminate the session
							// send the session terminate message
							final Jingle sessionTerminate = util.createSessionTerminateCancel(ftSession.fileTransferTargetJID, ftSession.streamId);

							ftSession.con.sendIqRequestAsync(sessionTerminate);
							return;
						}
					}
					catch (Throwable t) {/*no-op*/}
					
				}
				
				read = fileInstream.read(buffer);
				writtenSoFar += read;
			}
			
			System.out.println("In-banbd File transfer is successful.  Terminating the session");

			
			// send the session terminate message
			final Jingle sessionTerminate = util.createSessionTerminateSuccess(ftSession.fileTransferTargetJID, ftSession.streamId);

			ftSession.con.sendIqRequestAsync(sessionTerminate);
			
		}
		catch (Exception e)
		{
			System.out.println("Error when transfering the file via In-band file stream.  Terminating the session");
			
			// terminate the session
			// send the session terminate message
			final Jingle sessionTerminate = util.createSessionTerminateFailedTransport(ftSession.fileTransferTargetJID, ftSession.streamId);

			ftSession.con.sendIqRequestAsync(sessionTerminate);
		}
		finally 
		{
			jingleManager.unregisterJingleSessionHandler(ftSession.fileTransferTargetJID, ftSession.streamId, ftSession.sessionHandler);
			
			// assuming the closer of the output stream will send the appropriate byte stream close stanzas
		}		
	
	}
}
