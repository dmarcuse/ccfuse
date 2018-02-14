package me.apemanzilla.ccfuse.packets.client;

import jnr.ffi.Pointer;
import lombok.ToString;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.struct.FuseFileInfo;

/**
 * A packet from the client containing directory information
 * 
 * @author apemanzilla
 *
 */
@ToString(callSuper = true)
public class ClientReaddirPacket extends ClientPacket {
	private String[] contents;

	public int fillBuffer(Pointer buf, FuseFillDir fill, long offset, FuseFileInfo fi) {
		if (getError() != null) return getError().code;

		fill.apply(buf, ".", null, 0);
		fill.apply(buf, "..", null, 0);

		for (String s : contents)
			fill.apply(buf, s, null, 0);

		return 0;
	}
}
