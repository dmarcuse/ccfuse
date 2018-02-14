package me.apemanzilla.ccfuse.packets.server;

import lombok.ToString;

/**
 * A server packet that requests contents of a file
 * 
 * @author apemanzilla
 *
 */
@ToString(callSuper = true)
public class ServerReadPacket extends ServerPathPacket {
	private long size, offset;

	public ServerReadPacket(String path, long size, long offset) {
		super("read", path);
		this.size = size;
		this.offset = offset;
	}
}
