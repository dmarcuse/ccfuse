package me.apemanzilla.ccfuse.packets.client;

import ru.serce.jnrfuse.ErrorCodes;

/**
 * Error codes that may be returned by the client
 * 
 * @author apemanzilla
 *
 */
public enum ClientError {
	/**
	 * Given path doesn't exist
	 */
	NOPATH(-ErrorCodes.ENOENT()),

	/**
	 * Given path exists
	 */
	EXISTS(-ErrorCodes.EEXIST()),

	/**
	 * Given path isn't a directory
	 */
	NOTDIR(-ErrorCodes.ENOTDIR()),

	/**
	 * Given path isn't a file
	 */
	NOTFILE(-ErrorCodes.EISDIR()),

	/**
	 * Given path is read only
	 */
	READONLY(-ErrorCodes.EACCES()),

	/**
	 * Given path is not empty
	 */
	NOTEMPTY(-ErrorCodes.ENOTEMPTY()),

	/**
	 * Out of space
	 */
	NOSPACE(-ErrorCodes.ENOSPC());

	public final int code;

	private ClientError(int code) {
		this.code = code;
	}
}
