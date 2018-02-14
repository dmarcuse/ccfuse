package me.apemanzilla.ccfuse.packets.server;

import lombok.ToString;

/**
 * A server packet that specifies a path
 * 
 * @author apemanzilla
 */
@ToString(callSuper = true)
public class ServerPathPacket extends ServerPacket {
	private final String path;

	public ServerPathPacket(String operation, String path) {
		super(operation);
		this.path = path;
	}
}
