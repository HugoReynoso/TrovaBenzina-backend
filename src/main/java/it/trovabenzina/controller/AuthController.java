package it.trovabenzina.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.trovabenzina.dto.LoginRequestDto;
import it.trovabenzina.dto.LoginResponseDto;
import it.trovabenzina.service.AuthService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/login")
	public LoginResponseDto login(@Valid @RequestBody LoginRequestDto request) {
		return authService.login(request);
	}
}
