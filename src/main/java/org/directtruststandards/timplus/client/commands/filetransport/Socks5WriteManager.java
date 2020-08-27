package org.directtruststandards.timplus.client.commands.filetransport;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.jivesoftware.smackx.jingle.JingleManager;
import org.jivesoftware.smackx.jingle.JingleUtil;
import org.jivesoftware.smackx.jingle.element.Jingle;

public class Socks5WriteManager implements Runnable
{
	protected final FileTransferSession ftSession;
	
	protected final List<FileTransferDataListener> fileTransferDataListeners;
	
	protected final JingleUtil util;
	
	protected final JingleManager jingleManager;
	
	public Socks5WriteManager(FileTransferSession ftSession, List<FileTransferDataListener> fileTransferDataListeners)
	{
		this.ftSession = ftSession;
		
		this.fileTransferDataListeners = fileTransferDataListeners;
		
		this.util = new JingleUtil(ftSession.con);
		
		this.jingleManager = JingleManager.getInstanceFor(ftSession.con);
	}
	
	@SuppressWarnings("deprecation")
	@Override
	public void run()
	{
		System.out.println("Beginning the file transfer to the target.");
		
		try (final OutputStream outStream = ftSession.proxySocket.getOutputStream(); 
				final InputStream fileInstream = new BufferedInputStream(new FileInputStream(ftSession.sendFile)))
		{				
			// Allocate a 16K buffer
			byte[] buffer = new byte[16384];
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
			
			System.out.println("File transfer is successful.  Terminating the session");

			
			// send the session terminate message
			final Jingle sessionTerminate = util.createSessionTerminateSuccess(ftSession.fileTransferTargetJID, ftSession.streamId);

			ftSession.con.sendIqRequestAsync(sessionTerminate);
		}
		catch (Exception e)
		{
			System.out.println("Error when transfering the file.  Terminating the session");
			
			// terminate the session
			// send the session terminate message
			final Jingle sessionTerminate = util.createSessionTerminateFailedTransport(ftSession.fileTransferTargetJID, ftSession.streamId);

			ftSession.con.sendIqRequestAsync(sessionTerminate);
		}
		finally 
		{
			jingleManager.unregisterJingleSessionHandler(ftSession.fileTransferTargetJID, ftSession.streamId, ftSession.sessionHandler);
			// close the socket
			IOUtils.closeQuietly(ftSession.proxySocket);
		}
	}
}
