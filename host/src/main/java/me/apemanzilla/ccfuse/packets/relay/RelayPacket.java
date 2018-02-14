package me.apemanzilla.ccfuse.packets.relay;

import lombok.ToString;
import me.apemanzilla.ccfuse.packets.BasePacket;

/**
 * A base relay packet
 * 
 * @author apemanzilla
 *
 */
@ToString(callSuper = true)
public class RelayPacket extends BasePacket {
	public RelayPacket() {
		super(BasePacket.Source.RELAY);
	}
}
