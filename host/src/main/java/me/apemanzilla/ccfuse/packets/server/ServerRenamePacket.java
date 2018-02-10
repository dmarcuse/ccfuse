package me.apemanzilla.ccfuse.packets.server;

import lombok.ToString;

@ToString(callSuper = true)
public class ServerRenamePacket extends ServerPacket {
	private final String from, to;

	public ServerRenamePacket(String from, String to) {
		super("rename");
		this.from = from;
		this.to = to;
	}
}
