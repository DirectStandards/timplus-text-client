package org.directtruststandards.timplus.client.packets;

import java.security.cert.X509Certificate;

import org.jivesoftware.smack.packet.IQ;

public class CredRequest extends IQ
{
	public static final String NAMESPACE = "http://standards.directtrust.org/DS2019-02/filetransfer/proxy/auth#onetimeup";
	
	public static final String ELEMENT = "credRequest";
	
	public static final String CREDENTIALS_ELEMENT = "credentials";
	
	public static final String CA_ELEMENT = "ca";
	
	private String subject;
	
	private String secret;
	
	private X509Certificate proxyServerCA;
	
    public CredRequest() 
    {
        super(ELEMENT, NAMESPACE);
    }

	@Override
	public Type getType()
	{
		// TODO Auto-generated method stub
		return super.getType();
	}

	@Override
	public void setType(Type type)
	{
		// TODO Auto-generated method stub
		super.setType(type);
	}

	@Override
	public boolean isRequestIQ()
	{
		// TODO Auto-generated method stub
		return super.isRequestIQ();
	}

	@Override
	protected IQChildElementXmlStringBuilder getIQChildElementBuilder(IQChildElementXmlStringBuilder xml)
	{
        xml.setEmptyElement();
        return xml;
	}

	public String getSubject()
	{
		return subject;
	}

	public void setSubject(String subject)
	{
		this.subject = subject;
	}

	public String getSecret()
	{
		return secret;
	}

	public void setSecret(String secret)
	{
		this.secret = secret;
	}

	public X509Certificate getProxyServerCA()
	{
		return proxyServerCA;
	}

	public void setProxyServerCA(X509Certificate proxyServerCA)
	{
		this.proxyServerCA = proxyServerCA;
	}
    
    
}
