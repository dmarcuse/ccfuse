package me.apemanzilla.ccfuse;

public class InvalidMessageException extends RuntimeException {
	private static final long serialVersionUID = 6000831755791063789L;

	public InvalidMessageException(String message) {
		super(message);
	}
}
