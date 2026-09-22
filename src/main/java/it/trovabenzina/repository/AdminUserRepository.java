package it.trovabenzina.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import it.trovabenzina.entity.AdminUser;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

	Optional<AdminUser> findByEmailIgnoreCase(String email);
}
