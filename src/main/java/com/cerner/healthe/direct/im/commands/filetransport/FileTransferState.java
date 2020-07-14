package com.cerner.healthe.direct.im.commands.filetransport;

public enum FileTransferState
{
	SESSION_UNKNOWN,
	
	SESSION_INITIATE,
	
	SESSION_INITIATE_ACK,
	
	SESSION_ACCEPT,
	
	SESSION_ACCEPT_ACK,
	
	SESSION_INFO,
	
	CONTENT_REJECT,
	
	CONTENT_REMOVE,
	
	INITIATOR_CANDIDATE_USED,
	
	INITIATOR_CANDIDATE_USED_ACK,
	
	RESPONDER_CANDIDATE_USED,
	
	RESPONDER_CANDIDATE_USED_ACK,
	
	TRANSPORT_ACTIVATED,
	
	TRANSPORT_PROXY_ERROR,
	
	SESSION_TERIMINATE,
	

}