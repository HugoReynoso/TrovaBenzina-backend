package it.trovabenzina.exception;

public class MimitImportException extends RuntimeException {

	public MimitImportException(String message) {
		super(message);
	}

	public MimitImportException(String message, Throwable cause) {
		super(message, cause);
	}
}
