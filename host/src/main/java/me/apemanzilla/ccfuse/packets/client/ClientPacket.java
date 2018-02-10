package me.apemanzilla.ccfuse.packets.client;

import java.util.UUID;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class ClientPacket {
	private ClientError error;
	private UUID id;
	
	public int checkError() {
		return error == null ? 0 : error.code;
	}
}
