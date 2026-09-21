package it.trovabenzina.exception;

public class ProvinceNotFoundException extends ResourceNotFoundException {

	public ProvinceNotFoundException(Long id) {
		super("Province not found with id " + id);
	}
}
