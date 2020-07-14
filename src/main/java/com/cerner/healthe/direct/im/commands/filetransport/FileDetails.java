package com.cerner.healthe.direct.im.commands.filetransport;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.io.IOUtils;


public class FileDetails
{
	protected FileTime createdDtTm;
	protected byte[] hash;
	protected int size;
	protected String name;
	protected String mimeType;
	
	public static FileDetails fileToDetails(File file) throws IOException
	{
		return new FileDetails(file);
	}
	
	protected FileDetails(File file) throws IOException
	{
	    final BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
	    createdDtTm = attr.creationTime();
	    
	    size = (int)attr.size();
	    name = file.getName();
	    mimeType = getMimeType(file);
	    
	    
	    try (final InputStream fileStream = IOUtils.toBufferedInputStream(new FileInputStream(file));)
		{
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			
			// create a 1 MB buffer... useful for transfering large files
			byte [] buffer = new byte[1024000];
			
		    int sizeRead = -1;
		    while ((sizeRead = fileStream.read(buffer)) != -1) 
		        digest.update(buffer, 0, sizeRead);
		    
		    hash = new byte[digest.getDigestLength()];
		    hash = digest.digest();
		} 
	    catch (NoSuchAlgorithmException e)
		{
	    	throw new IOException("Can't create file digest.", e);
		}
	}
	
	protected String getMimeType(File file) throws IOException
	{
		// for now , use the Files built in content type probe
		
		return Files.probeContentType(file.toPath());
	}
}
