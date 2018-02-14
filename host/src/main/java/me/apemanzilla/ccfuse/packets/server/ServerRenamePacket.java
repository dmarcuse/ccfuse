package me.apemanzilla.ccfuse.packets.server;

import lombok.ToString;

/**
 * A server packet that requests a rename (aka move) of a given file/directory
 * 
 * @author apemanzilla
 *
 */
@ToString(callSuper = true)
public class ServerRenamePacket extends ServerPacket {
	private final String from, to;

	public ServerRenamePacket(String from, String to) {
		super("rename");
		this.from = from;
		this.to = to;
	}
}
