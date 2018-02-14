package me.apemanzilla.ccfuse.packets.server;

import com.google.gson.annotations.JsonAdapter;

import jnr.ffi.Pointer;
import lombok.ToString;
import me.apemanzilla.ccfuse.packets.Base64Adapter;

/**
 * A server packet that requests given data be written to a file by the client
 * 
 * @author apemanzilla
 *
 */
@ToString(callSuper = true)
public class ServerWritePacket extends ServerPathPacket {
	private final long offset;

	@JsonAdapter(Base64Adapter.class)
	private final byte[] data;

	public ServerWritePacket(String path, Pointer buf, long size, long offset) {
		super("write", path);
		this.offset = offset;
		data = new byte[(int) size];
		buf.get(offset, data, 0, (int) size);
	}
}
