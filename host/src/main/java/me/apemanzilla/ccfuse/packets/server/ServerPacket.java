package me.apemanzilla.ccfuse.packets.server;

import java.util.UUID;

import lombok.Getter;
import lombok.ToString;
import me.apemanzilla.ccfuse.packets.BasePacket;

/**
 * A base server packet
 * 
 * @author apemanzilla
 *
 */
@Getter
@ToString(callSuper = true)
public class ServerPacket extends BasePacket {
	/**
	 * The ID of this packet - will match the id of the client response packet
	 */
	private final UUID id;

	/**
	 * The operation requested by the server (operation-specific data is defined
	 * by subclasses)
	 */
	private final String operation;

	protected ServerPacket(String operation) {
		super(BasePacket.Source.SERVER);
		this.id = UUID.randomUUID();
		this.operation = operation;
	}
}
