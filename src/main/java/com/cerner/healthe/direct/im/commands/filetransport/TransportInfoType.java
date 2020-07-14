package com.cerner.healthe.direct.im.commands.filetransport;

import org.jivesoftware.smackx.jingle.element.JingleContent;
import org.jivesoftware.smackx.jingle.element.JingleContentTransportInfo;
import org.jivesoftware.smackx.jingle.transports.jingle_s5b.elements.JingleS5BTransportInfo;

public enum TransportInfoType
{
	UNKNOWN,
	
	CANDIDATE_USED,
	
	CANDIDATE_ACTIVATED,
	
	CANDIATE_ERROR,
	
	PROXY_ERROR;
	
	public static TransportInfoType getTransportInfoType(JingleContent content)
	{		
		if (content.getTransport() != null && content.getTransport().getInfo() != null)
		{
			JingleContentTransportInfo info = content.getTransport().getInfo();
			if (info instanceof JingleS5BTransportInfo.CandidateActivated)
				return TransportInfoType.CANDIDATE_ACTIVATED;
			else if (info instanceof JingleS5BTransportInfo.CandidateUsed)
				return TransportInfoType.CANDIDATE_USED;
			else if (info instanceof JingleS5BTransportInfo.CandidateError)
				return TransportInfoType.CANDIATE_ERROR;
			else if (info instanceof JingleS5BTransportInfo.ProxyError)
				return TransportInfoType.PROXY_ERROR;
			
		}
		
		return TransportInfoType.UNKNOWN;
		
	}
}
