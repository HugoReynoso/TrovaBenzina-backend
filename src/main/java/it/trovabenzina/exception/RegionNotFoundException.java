package it.trovabenzina.exception;

public class RegionNotFoundException extends ResourceNotFoundException {

	public RegionNotFoundException(Long id) {
		super("Region not found with id " + id);
	}
}
