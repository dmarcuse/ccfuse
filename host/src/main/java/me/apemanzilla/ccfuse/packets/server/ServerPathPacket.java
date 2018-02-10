package me.apemanzilla.ccfuse.packets.server;

import lombok.ToString;

/**
 * A packet containing an arbitrary path
 * 
 * @author apemanzilla
 */
@ToString(callSuper=true)
public class ServerPathPacket extends ServerPacket {
	private final String path;
	
	public ServerPathPacket(String operation, String path) {
		super(operation);
		this.path = path;
	}
}
