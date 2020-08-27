package org.directtruststandards.timplus.client.packets;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

import org.jivesoftware.smack.provider.IQProvider;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class CredRequestProvider extends IQProvider<CredRequest>
{
	public CredRequestProvider()
	{
		
	}
	
    @Override
    public CredRequest parse(XmlPullParser parser, int initialDepth) throws XmlPullParserException, IOException
    {
    	boolean done = false;
    	
    	final CredRequest cred = new CredRequest();
    	
        int eventType;
        String elementName;
        while (!done) 
        {
            eventType = parser.next();
            elementName = parser.getName();
            if (eventType == XmlPullParser.START_TAG) 
            {
                if (elementName.equals(CredRequest.CREDENTIALS_ELEMENT)) 
                {
                	cred.setSubject(parser.getAttributeValue("", "subject"));
                	cred.setSecret(parser.getAttributeValue("", "secret"));
                }
                else if (elementName.equals(CredRequest.CA_ELEMENT)) 
                {
                	try
                	{
	                	final byte[] certDEREncoded = Base64.getDecoder().decode(parser.nextText());
	                	final  CertificateFactory cf = CertificateFactory.getInstance("X.509");
	                	final X509Certificate cert = (X509Certificate)cf.generateCertificate(new ByteArrayInputStream(certDEREncoded));
	                	cred.setProxyServerCA(cert);
                	}
                	catch (Exception e)
                	{
                		System.out.println("Failed to parse proxy server CA certificate: " + e.getMessage());
                	}
                }

            }
            else if (eventType == XmlPullParser.END_TAG) 
            {

                if (elementName.equals("credRequest")) 
                {
                    done = true;
                }
            }
        }
    	
    	return cred;
    }
}
