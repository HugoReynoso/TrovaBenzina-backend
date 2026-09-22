package it.trovabenzina.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.trovabenzina.dto.LoginRequestDto;
import it.trovabenzina.dto.LoginResponseDto;
import it.trovabenzina.exception.ResourceNotFoundException;
import it.trovabenzina.repository.AdminUserRepository;
import it.trovabenzina.security.JwtService;

@Service
public class AuthService {

	private final AdminUserRepository adminUserRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public AuthService(AdminUserRepository adminUserRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
		this.adminUserRepository = adminUserRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	@Transactional(readOnly = true)
	public LoginResponseDto login(LoginRequestDto request) {
		var admin = adminUserRepository.findByEmailIgnoreCase(request.email())
				.orElseThrow(() -> new ResourceNotFoundException("Invalid admin credentials"));
		if (!passwordEncoder.matches(request.password(), admin.getPasswordHash())) {
			throw new ResourceNotFoundException("Invalid admin credentials");
		}
		return new LoginResponseDto(jwtService.createToken(admin.getEmail()), "Bearer", jwtService.expiresInSeconds());
	}
}
