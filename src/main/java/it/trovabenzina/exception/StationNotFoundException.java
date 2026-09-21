package it.trovabenzina.exception;

public class StationNotFoundException extends ResourceNotFoundException {

	public StationNotFoundException(Long id) {
		super("Station not found with id " + id);
	}
}
