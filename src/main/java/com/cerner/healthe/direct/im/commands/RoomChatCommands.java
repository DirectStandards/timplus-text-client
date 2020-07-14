package com.cerner.healthe.direct.im.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smackx.disco.packet.DiscoverItems;
import org.jivesoftware.smackx.muc.HostedRoom;
import org.jivesoftware.smackx.muc.InvitationListener;
import org.jivesoftware.smackx.muc.MucEnterConfiguration;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.jivesoftware.smackx.muc.packet.MUCUser.Invite;
import org.jxmpp.jid.DomainBareJid;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.EntityJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;
import org.nhindirect.common.tooling.Command;
import org.nhindirect.common.tooling.StringArrayUtil;

import com.cerner.healthe.direct.im.printers.HostedRoomPrinter;

public class RoomChatCommands
{
    private static final String LIST_ROOMS = "List multi user chat rooms the are available.";
    
    private static final String LIST_JOINED_ROOMS = "List multi user chat rooms that you have joined.";
    
    private static final String JOIN_ROOM = "Joins a chat room." +
    		"\r\n  room " +
            "\r\n\t room: The Jid of the room to join.";
    
    private static final String LEAVE_ROOM = "Leaves a chat room." +
    		"\r\n  room " +
            "\r\n\t room: The Jid of the room to leave.";
    
    private static final String SEND_ROOM_MESSAGE = "Sends a message to a group" +
    		"\r\n  room message " +
            "\r\n\t room: The Jid of the room." +
    		"\r\n\t message: The message to send to the room.  This should eclose in a double quote (\" \") if the messge contains spaces.";
    
    protected MultiUserChatManager manager;
    
	protected AbstractXMPPConnection con;
	
    protected HostedRoomPrinter roomPrinter;
    
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
				System.out.println("Received invitaiton to join room " + room.getRoom().asEntityBareJidString());
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
	}
	
	@Command(name = "ListRooms", usage = LIST_ROOMS)
	public void listChatRooms(String[] args) throws Exception
	{
		final List<HostedRoom> rooms = getHostedRooms();
		
		if (rooms.isEmpty())
			System.out.println("No rooms found");
		else
			roomPrinter.printRecords(rooms);
	
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
		final String room = StringArrayUtil.getRequiredValue(args, 0);
		
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
			chat.join(mucConfig);
		}
		catch (Exception e)
		{
			
		}
		chat.addMessageListener(new MessageListener()
		{

			@Override
			public void processMessage(Message message)
			{
				
				if (message.getBody() != null)
				{

					  System.out.println("New message in group " + message.getFrom().asBareJid() + ": " + message.getBody());
					  System.out.println(">");
				}
			}
			
		});
		
		
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
}
