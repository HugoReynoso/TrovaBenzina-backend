package it.trovabenzina.exception;

public class RegionAlreadyExistsException extends RuntimeException {

    public RegionAlreadyExistsException(String message) {
        super(message);
    }
}