package me.apemanzilla.ccfuse.packets.server;

import java.util.UUID;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class ServerPacket {
	private final UUID id;
	private final String operation;

	protected ServerPacket(String operation) {
		this.id = UUID.randomUUID();
		this.operation = operation;
	}
}
