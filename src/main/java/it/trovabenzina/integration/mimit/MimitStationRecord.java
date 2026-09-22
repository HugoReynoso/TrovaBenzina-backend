package it.trovabenzina.integration.mimit;

public record MimitStationRecord(String mimitId, String name, String brand, String address, String municipality,
		String provinceCode, Double latitude, Double longitude) {
}
