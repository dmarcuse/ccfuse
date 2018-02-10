package me.apemanzilla.ccfuse.packets.client;

import com.google.gson.annotations.JsonAdapter;

import jnr.ffi.Pointer;
import lombok.ToString;
import me.apemanzilla.ccfuse.packets.Base64Adapter;

@ToString(callSuper = true)
public class ClientReadPacket extends ClientPacket {
	@JsonAdapter(Base64Adapter.class)
	private byte[] data;

	public int copy(Pointer buf) {
		if (getError() != null) return getError().code;
		if (data == null || data.length == 0) return 0;

		buf.put(0, data, 0, data.length);

		return data.length;
	}
}
