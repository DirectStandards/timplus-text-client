package com.cerner.healthe.direct.im.commands.filetransport;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.io.IOUtils;
import org.jivesoftware.smackx.jingle.JingleManager;
import org.jivesoftware.smackx.jingle.JingleUtil;
import org.jivesoftware.smackx.jingle.element.Jingle;
import org.jivesoftware.smackx.jingle.element.JingleReason;

public class Socks5ReadManager implements Runnable
{
	protected final FileTransferSession ftSession;
	
	protected JingleUtil util;
	
	protected final JingleManager jingleManager;
	
	public Socks5ReadManager(FileTransferSession ftSession)
	{
		this.ftSession = ftSession;
		
		this.util = new JingleUtil(ftSession.con);
		
		jingleManager = JingleManager.getInstanceFor(ftSession.con);
	}
	
	@SuppressWarnings("deprecation")
	@Override
	public void run()
	{
		final ReceiveFileStatusListener listener = new ReceiveFileStatusListener(ftSession.fileSize);
		
		System.out.println("Starting to read file transfer data");
		
		OutputStream fStream = null;
		try (InputStream proxyInputStream = ftSession.proxySocket.getInputStream())
		{
			final File writeFile = new File(ftSession.fileName);
			
			writeFile.createNewFile();
			fStream = new BufferedOutputStream(new FileOutputStream(writeFile));
			
			long readSoFar = 0;
			byte[] buffer = new byte[16384];
			int read = proxyInputStream.read(buffer);
			readSoFar += read;
			while(read > -1) 
			{
				if (read > 0)
					fStream.write(buffer, 0, read);
				

					try 
					{
						if (listener.dataTransfered(readSoFar) != 0)
						{
							// the transfer was interrupted by the listener
							
							System.out.println("File transfer was interrupted by a transfer listener.  Terminating the session");
							
							// terminate the session
							// send the session terminate message
							final Jingle sessionTerminate = util.createSessionTerminateCancel(ftSession.initiatorJID, ftSession.streamId);

							ftSession.con.sendIqRequestAsync(sessionTerminate);
							
							return;
						}
					}
					catch (Throwable t) {/*no-op*/}
					
				
				read = proxyInputStream.read(buffer);
				
				readSoFar += read;
			}
			
			fStream.flush();
			System.out.println("Done reading the file transfer.  Terminating the session.");
			
			// send the session terminate message
			final Jingle sessionTerminate = util.createSessionTerminateSuccess(ftSession.initiatorJID, ftSession.streamId);

			ftSession.con.sendIqRequestAsync(sessionTerminate);
		}
		catch (Exception e)
		{
			System.out.println("Failure detected during file transfer");

			// terminate the session with a cancel.. don't try a fall back because
			// we already determined that we can establish a mutual proxy connection
			// fall backs should be used only if we can't agree upon a mutual proxy connection

			final Jingle sessionTerminate = util.createSessionTerminate(ftSession.initiatorJID, ftSession.streamId, JingleReason.FailedTransport);
			ftSession.con.sendIqRequestAsync(sessionTerminate);
			
			
		}
		finally
		{
			jingleManager.unregisterJingleSessionHandler(ftSession.initiatorJID, ftSession.streamId, ftSession.sessionHandler);
			IOUtils.closeQuietly(fStream);
			IOUtils.closeQuietly(ftSession.proxySocket);
		}
		
	}
}
