package it.trovabenzina.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import it.trovabenzina.entity.AdminLog;

public interface AdminLogRepository extends JpaRepository<AdminLog, Long> {

	List<AdminLog> findTop50ByOrderByCreatedAtDesc();
}
