package me.apemanzilla.ccfuse.packets.client;

import java.util.UUID;

import lombok.Getter;
import lombok.ToString;
import me.apemanzilla.ccfuse.packets.BasePacket;

/**
 * A base client packet
 * 
 * @author apemanzilla
 */
@Getter
@ToString(callSuper = true)
public class ClientPacket extends BasePacket {
	/**
	 * The error code returned by the client - may be null if the operation was
	 * successful
	 */
	private ClientError error;

	public ClientPacket() {
		super(BasePacket.Source.CLIENT);
	}

	/**
	 * The id of the corresponding server packet that was sent
	 */
	private UUID id;

	public int checkError() {
		return error == null ? 0 : error.code;
	}
}
