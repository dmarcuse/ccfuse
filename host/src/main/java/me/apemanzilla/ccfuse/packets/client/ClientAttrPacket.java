package me.apemanzilla.ccfuse.packets.client;

import lombok.ToString;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.struct.FileStat;

/**
 * A packet from the client containing attribute data
 * 
 * @author apemanzilla
 *
 */
@ToString(callSuper = true)
public class ClientAttrPacket extends ClientPacket implements FileAttributes {
	private boolean exists;
	private Boolean isDir, isReadOnly;
	private Long size;

	/**
	 * Copy attribute data to the given FileStat struct
	 */
	@Override
	public int setMode(FileStat stat) {
		if (getError() != null) return getError().code;

		if (!exists) {
			return -ErrorCodes.ENOENT();
		} else {
			int mode = FileStat.ALL_READ | (isDir ? FileStat.S_IFDIR : FileStat.S_IFREG);
			if (!isReadOnly) mode |= FileStat.S_IWUSR;

			stat.st_mode.set(mode);
			if (!isDir) stat.st_size.set(size);

			return 0;
		}
	}
}
