package it.trovabenzina.exception;

public class CityNotFoundException extends ResourceNotFoundException {

	public CityNotFoundException(Long id) {
		super("City not found with id " + id);
	}
}
