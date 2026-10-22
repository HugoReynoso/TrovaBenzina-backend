package it.trovabenzina.exception;

public class MimitDownloadException extends MimitImportException {
	public MimitDownloadException(String message) {
		super(message);
	}

	public MimitDownloadException(String message, Throwable cause) {
		super(message, cause);
	}
}
