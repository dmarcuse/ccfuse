package me.apemanzilla.ccfuse.packets;

import lombok.*;

/**
 * Base packet type
 * 
 * @author apemanzilla
 *
 */
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class BasePacket {
	public static enum Source {
		/**
		 * Packet was sent from the server
		 */
		SERVER,

		/**
		 * Packet was sent from the client
		 */
		CLIENT,

		/**
		 * Packet was sent from the relay
		 */
		RELAY
	}

	@Getter
	@NonNull
	private Source source;
}
