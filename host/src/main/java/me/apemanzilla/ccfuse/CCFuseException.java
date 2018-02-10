package me.apemanzilla.ccfuse;

public class CCFuseException extends RuntimeException {
	private static final long serialVersionUID = 2228783054988852903L;

	public CCFuseException(String message) {
		super(message);
	}

	public CCFuseException(String message, Throwable cause) {
		super(message, cause);
	}
}
