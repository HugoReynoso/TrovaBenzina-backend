package it.trovabenzina.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class JwtService {

	private final SecurityProperties properties;
	private final ObjectMapper objectMapper;

	public JwtService(SecurityProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	public String createToken(String subject) {
		long expiresAt = Instant.now().plusSeconds(properties.jwtExpirationMinutes() * 60).getEpochSecond();
		String header = encodeJson("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
		String payload = encodeJson("{\"sub\":\"" + subject + "\",\"role\":\"ADMIN\",\"exp\":" + expiresAt + "}");
		String signature = sign(header + "." + payload);
		return header + "." + payload + "." + signature;
	}

	public String validateAndGetSubject(String token) {
		try {
			String[] parts = token.split("\\.");
			if (parts.length != 3 || !sign(parts[0] + "." + parts[1]).equals(parts[2])) {
				return null;
			}
			JsonNode payload = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
			if (payload.get("exp").asLong() < Instant.now().getEpochSecond()) {
				return null;
			}
			return payload.get("sub").asText();
		} catch (RuntimeException | java.io.IOException ex) {
			return null;
		}
	}

	public long expiresInSeconds() {
		return properties.jwtExpirationMinutes() * 60;
	}

	private String encodeJson(String json) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
	}

	private String sign(String value) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception ex) {
			throw new IllegalStateException("Unable to sign JWT", ex);
		}
	}
}
