package it.trovabenzina.security;

import java.util.Arrays;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import it.trovabenzina.repository.AdminUserRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final org.springframework.core.env.Environment environment;

	public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, org.springframework.core.env.Environment environment) {
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
		this.environment = environment;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http.csrf(csrf -> csrf.disable())
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/price-reports").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/regions", "/api/provinces", "/api/cities/**", "/api/stations/**")
						.permitAll()
						.requestMatchers("/api/admin/**").hasRole("ADMIN")
						.anyRequest().permitAll())
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
				.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	UserDetailsService userDetailsService(AdminUserRepository adminUserRepository) {
		return email -> adminUserRepository.findByEmailIgnoreCase(email)
				.map(admin -> User.withUsername(admin.getEmail()).password(admin.getPasswordHash()).roles("ADMIN").build())
				.orElseThrow(() -> new UsernameNotFoundException("Admin user not found"));
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		String origins = environment.getProperty("app.cors.allowed-origins", "http://localhost:3000");
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toList());
		configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PATCH", "OPTIONS"));
		configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept"));
		configuration.setAllowCredentials(false);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}
