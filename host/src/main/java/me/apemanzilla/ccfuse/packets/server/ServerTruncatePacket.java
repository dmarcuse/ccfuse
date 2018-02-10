package me.apemanzilla.ccfuse.packets.server;

import lombok.ToString;

@ToString(callSuper = true)
public class ServerTruncatePacket extends ServerPathPacket {
	private final long size;

	public ServerTruncatePacket(String path, long size) {
		super("truncate", path);
		this.size = size;
	}
}
