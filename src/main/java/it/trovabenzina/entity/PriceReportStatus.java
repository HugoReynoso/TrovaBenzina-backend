package it.trovabenzina.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PriceReportStatus {
	PENDING("pending"),
	APPROVED("approved"),
	REJECTED("rejected");

	private final String value;

	PriceReportStatus(String value) {
		this.value = value;
	}

	@JsonValue
	public String getValue() {
		return value;
	}

	@JsonCreator
	public static PriceReportStatus fromValue(String value) {
		for (PriceReportStatus status : values()) {
			if (status.value.equalsIgnoreCase(value)) {
				return status;
			}
		}
		throw new IllegalArgumentException("Invalid report status: " + value);
	}
}
