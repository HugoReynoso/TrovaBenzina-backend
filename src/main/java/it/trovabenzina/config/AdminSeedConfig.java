package it.trovabenzina.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import it.trovabenzina.entity.AdminUser;
import it.trovabenzina.repository.AdminUserRepository;
import it.trovabenzina.security.SecurityProperties;

@Configuration
public class AdminSeedConfig {

	@Bean
	ApplicationRunner seedAdminUser(AdminUserRepository adminUserRepository, PasswordEncoder passwordEncoder,
			SecurityProperties properties) {
		return args -> adminUserRepository.findByEmailIgnoreCase(properties.adminEmail()).orElseGet(() -> {
			AdminUser admin = new AdminUser();
			admin.setEmail(properties.adminEmail());
			admin.setPasswordHash(passwordEncoder.encode(properties.adminPassword()));
			return adminUserRepository.save(admin);
		});
	}
}
