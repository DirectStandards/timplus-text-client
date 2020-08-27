package org.directtruststandards.timplus.client.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.directtruststandards.timplus.client.printers.HostedRoomPrinter;
import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.StanzaError;
import org.jivesoftware.smack.packet.StanzaError.Type;
import org.jivesoftware.smackx.disco.packet.DiscoverItems;
import org.jivesoftware.smackx.muc.HostedRoom;
import org.jivesoftware.smackx.muc.InvitationListener;
import org.jivesoftware.smackx.muc.MucEnterConfiguration;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.MultiUserChat.MucCreateConfigFormHandle;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.jivesoftware.smackx.muc.packet.MUCUser.Invite;
import org.jxmpp.jid.DomainBareJid;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.EntityJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;
import org.nhindirect.common.tooling.Command;
import org.nhindirect.common.tooling.StringArrayUtil;

public class RoomChatCommands implements MessageListener
{
    private static final String LIST_ROOMS = "List multi user chat rooms the are available.";
    
    private static final String LIST_JOINED_ROOMS = "List multi user chat rooms that you have joined.";
    
    private static final String JOIN_ROOM = "Joins a chat room." +
    		"\r\n  room " +
            "\r\n\t room: The Jid of the room to join.  If a domain is not provided, an attempt will be made to use the connected users default chat room sub domain.";
    
    private static final String LEAVE_ROOM = "Leaves a chat room." +
    		"\r\n  room " +
            "\r\n\t room: The Jid of the room to leave.";
    
    private static final String SEND_ROOM_MESSAGE = "Sends a message to a group" +
    		"\r\n  room message " +
            "\r\n\t room: The Jid of the room." +
    		"\r\n\t message: The message to send to the room.  This should eclose in a double quote (\" \") if the messge contains spaces.";
    
    private static final String INVITE_TO_ROOM = "Invites another user into a room that you are a member of" +
    		"\r\n  room invitee " +
            "\r\n\t room: The Jid of the room to invite the user to" +
    		"\r\n\t invitee: The JID of the user to invite into the room.";
    
    protected MultiUserChatManager manager;
    
	protected AbstractXMPPConnection con;
	
    protected HostedRoomPrinter roomPrinter;
    
    protected DomainBareJid defaultGroupChatDomain;
    
	public RoomChatCommands(AbstractXMPPConnection con)
	{
		init(con);
		
		roomPrinter = new HostedRoomPrinter();
	}
	
	
	public void init(AbstractXMPPConnection con)
	{
		this.con = con;
		
		manager = MultiUserChatManager.getInstanceFor(con);
		
		manager.addInvitationListener(new InvitationListener()
		{

			@Override
			public void invitationReceived(XMPPConnection conn, MultiUserChat room, EntityJid inviter,
					String reason, String password, Message message, Invite invitation)
			{
				// Auto accept invitation
				if (room.isJoined())
				{
					System.out.println("Received invitaiton to join room " + room.getRoom().asEntityBareJidString() + "  You are already in this room.");
					return;
				}
				System.out.println(inviter.asEntityBareJidString() + " has invited you to join room " + room.getRoom().asEntityBareJidString());
				try
				{
					joinChatRoom(new String[] {room.getRoom().asEntityBareJidString()});
				}
				catch (Exception e)
				{
					// no-op
				}
			}
			
		});
		
		try
		{
			final List<DomainBareJid> chatDomains = manager.getMucServiceDomains();
			if (!chatDomains.isEmpty())
			{
				/*
				 * TIM+ service providers should define a single group chat subdomain
				 */
				defaultGroupChatDomain = chatDomains.get(0);
			}
		}
		catch (Exception e)
		{
			
		}
	}
	
	@Command(name = "ListRooms", usage = LIST_ROOMS)
	public void listChatRooms(String[] args) throws Exception
	{

		System.out.println("All rooms are hidden.  You can not list existing rooms; only rooms you have joined.  Try the ListJoinedRooms command");

	
	}
	
	@Command(name = "ListJoinedRooms", usage = LIST_JOINED_ROOMS)
	public void listJoinedRooms(String[] args) throws Exception
	{
		final Set<EntityBareJid> joinedRooms = manager.getJoinedRooms();
		

		
		if (joinedRooms.isEmpty())
		{
			System.out.println("No rooms joined");
			return;
		}
		
		final List<HostedRoom> rooms = 
				joinedRooms.stream().map(jid -> 
				{
					final DiscoverItems.Item item = new DiscoverItems.Item(jid);
					item.setName(jid.asEntityBareJidString());
					final HostedRoom room = new HostedRoom(item);
					return room;
				})
				.collect(Collectors.toList());
				
		roomPrinter.printRecords(rooms);
	
	}
	
	
	@Command(name = "JoinChatRoom", usage = JOIN_ROOM)
	public void joinChatRoom(String[] args) throws Exception
	{
		String room = StringArrayUtil.getRequiredValue(args, 0);
		
		if (!room.contains("@") && defaultGroupChatDomain != null)
		{
			// default to the domain's group chat domain
			room += "@" + defaultGroupChatDomain.toString();
		}
		
		final MultiUserChat chat = manager.getMultiUserChat(JidCreate.entityBareFrom(room));
		final Resourcepart nickname = Resourcepart.from(con.getUser().getLocalpart().toString());
		
		if (chat.isJoined())
		{
			System.out.println("You have already joined this chat room");
			return;
		}
		
		MucEnterConfiguration mucConfig = chat.getEnterConfigurationBuilder(nickname).build();
		
		try
		{
			final MucCreateConfigFormHandle createConfig = chat.createOrJoin(mucConfig);
			
			if (createConfig == null)
			{
				System.out.println("Failed to join room");
				return;
			}
			createConfig.makeInstant();
				
		}
		catch (XMPPException.XMPPErrorException e)
		{
			StanzaError error = e.getStanzaError();
			if (error != null && error.getType() == Type.AUTH)
				System.out.println("You are not allowed to join this room.  It most likely already exists have not you been invited to this room.");
			else
				System.out.println("Failed to join room: " + e.getMessage());
			return;
		}
		catch (Exception e)
		{
			System.out.println("Failed to join room: " + e.getMessage());
			return;
		}
		chat.addMessageListener(this);
		
		
		System.out.println("Joined room " + room);
	}
	
	@Command(name = "LeaveChatRoom", usage = LEAVE_ROOM)
	public void leaveChatRoom(String[] args) throws Exception
	{
		final String room = StringArrayUtil.getRequiredValue(args, 0);
		
		final Set<EntityBareJid> joinedRooms = manager.getJoinedRooms();
		
		for (EntityBareJid foundRoom : joinedRooms)
		{
			if (foundRoom.equals(room))
			{
				final MultiUserChat chat = manager.getMultiUserChat(JidCreate.entityBareFrom(room));
				chat.leave();
				chat.removeMessageListener(this);
				
				System.out.println("Leaving room " + room);
				return;
			}
		}
		
		System.out.println("You are not currently in this room");
		
	}
	
	@Command(name = "SendRoomMessage", usage = SEND_ROOM_MESSAGE)
	public void sendRoomMessage(String[] args) throws Exception
	{
		final String room = StringArrayUtil.getRequiredValue(args, 0);
		final String message = StringArrayUtil.getRequiredValue(args, 1);
		
		final MultiUserChat chat = manager.getMultiUserChat(JidCreate.entityBareFrom(room));
		
		if (!chat.isJoined())
		{
			System.out.println("You are not currently in this room");
			return;
		}
		
		chat.sendMessage(message);
	}
	
	@Command(name = "InviteToRoom", usage = INVITE_TO_ROOM)
	public void inviteToRoom(String[] args) throws Exception
	{
		final String room = StringArrayUtil.getRequiredValue(args, 0);
		final String invitee = StringArrayUtil.getRequiredValue(args, 1);
		
		final MultiUserChat chat = manager.getMultiUserChat(JidCreate.entityBareFrom(room));
		
		
		
		if (!chat.isJoined())
		{
			System.out.println("You are not currently in this room");
			return;
		}
		try
		{
			chat.invite(JidCreate.entityBareFrom(invitee), "");
		}
		catch (Exception e)
		{
			System.out.println("Failed to inviate user to room.");
		}
		
		System.out.println("Invitation successfuly sent to " + invitee);
	}
	
	
	protected List<HostedRoom> getHostedRooms() throws Exception
	{
		final List<HostedRoom> rooms = new ArrayList<>();

		final List<DomainBareJid> chatDomains = manager.getMucServiceDomains();

		for (DomainBareJid domain : chatDomains)
		{
			rooms.addAll(manager.getRoomsHostedBy(domain).values());
		}
		
		return rooms;
	}
	
	@Override
	public void processMessage(Message message)
	{
		
		if (message.getBody() != null)
		{

			  System.out.println("New message in group " + message.getFrom().asBareJid() + ": " + message.getBody());
			  System.out.print(">");
		}
	}
	
}
