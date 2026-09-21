package it.trovabenzina.exception;

public class FuelTypeNotFoundException extends ResourceNotFoundException {

	public FuelTypeNotFoundException(String code) {
		super("Fuel type not found with code " + code);
	}
}
