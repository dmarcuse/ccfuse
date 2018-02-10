package me.apemanzilla.ccfuse.packets.server;

import lombok.ToString;

@ToString(callSuper = true)
public class ServerReadPacket extends ServerPathPacket {
	private long size, offset;

	public ServerReadPacket(String path, long size, long offset) {
		super("read", path);
		this.size = size;
		this.offset = offset;
	}
}
