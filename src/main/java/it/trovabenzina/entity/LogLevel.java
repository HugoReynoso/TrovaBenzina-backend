package it.trovabenzina.entity;

import com.fasterxml.jackson.annotation.JsonValue;

public enum LogLevel {
	INFO("info"),
	WARNING("warning"),
	ERROR("error");

	private final String value;

	LogLevel(String value) {
		this.value = value;
	}

	@JsonValue
	public String getValue() {
		return value;
	}
}
