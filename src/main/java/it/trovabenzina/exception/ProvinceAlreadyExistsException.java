package it.trovabenzina.exception;

public class ProvinceAlreadyExistsException extends RuntimeException {

    public ProvinceAlreadyExistsException(String message) {
        super(message);
    }
}