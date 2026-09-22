package it.trovabenzina.dto;

import java.time.Instant;

import it.trovabenzina.entity.LogLevel;

public record AdminLogResponseDto(Long id, LogLevel level, String area, String message, Instant createdAt) {
}
