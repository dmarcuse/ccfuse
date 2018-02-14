package me.apemanzilla.ccfuse.packets.relay;

import lombok.Getter;
import lombok.ToString;

/**
 * Used by relays to convey the state of the client (CC computer)
 * 
 * @author apemanzilla
 *
 */
@Getter
@ToString(callSuper = true)
public class RelayConnectionStatusPacket extends RelayPacket {
	public static enum Status {
		/**
		 * The client (CC computer) is connected
		 */
		CONNECTED,

		/**
		 * The client (CC computer) is disconnected
		 */
		DISCONNECTED
	}

	private Status status;

	public RelayConnectionStatusPacket(Status status) {
		this.status = status;
	}
}
