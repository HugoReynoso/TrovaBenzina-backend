package it.trovabenzina.dto;

public record LoginResponseDto(String token, String tokenType, long expiresInSeconds) {
}
