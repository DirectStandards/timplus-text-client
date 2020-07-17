package com.cerner.healthe.direct.im.commands.filetransport;

import java.io.File;
import java.net.Socket;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smackx.bytestreams.socks5.packet.Bytestream;
import org.jivesoftware.smackx.jingle.JingleSessionHandler;
import org.jxmpp.jid.EntityFullJid;

public class FileTransferSession
{
	protected String streamId;
	
	protected Bytestream.StreamHost selectedStreamhost;
	
	protected EntityFullJid fileTransferTargetJID;
	
	protected EntityFullJid initiatorJID;
	
	protected String selectedCandidateId; 
	
	protected Socket proxySocket; 
	
	protected String fileName;
	
	protected File sendFile;
	
	protected long fileSize;
	
	protected JingleSessionHandler sessionHandler;
	
	protected AbstractXMPPConnection con;
}
