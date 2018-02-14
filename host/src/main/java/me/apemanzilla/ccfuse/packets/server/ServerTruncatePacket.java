package me.apemanzilla.ccfuse.packets.server;

import lombok.ToString;

/**
 * A server packet that requests the client truncate a file to a given size
 * 
 * @author apemanzilla
 *
 */
@ToString(callSuper = true)
public class ServerTruncatePacket extends ServerPathPacket {
	private final long size;

	public ServerTruncatePacket(String path, long size) {
		super("truncate", path);
		this.size = size;
	}
}
