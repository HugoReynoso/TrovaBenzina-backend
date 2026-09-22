package it.trovabenzina.service;

import java.time.ZoneOffset;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.trovabenzina.dto.AdminLogResponseDto;
import it.trovabenzina.entity.AdminLog;
import it.trovabenzina.entity.LogLevel;
import it.trovabenzina.repository.AdminLogRepository;

@Service
public class AdminLogService {

	private final AdminLogRepository adminLogRepository;

	public AdminLogService(AdminLogRepository adminLogRepository) {
		this.adminLogRepository = adminLogRepository;
	}

	@Transactional
	public void log(LogLevel level, String area, String message) {
		AdminLog entry = new AdminLog();
		entry.setLevel(level);
		entry.setArea(area);
		entry.setMessage(message);
		adminLogRepository.save(entry);
	}

	@Transactional(readOnly = true)
	public List<AdminLogResponseDto> findLatest() {
		return adminLogRepository.findTop50ByOrderByCreatedAtDesc().stream()
				.map(log -> new AdminLogResponseDto(log.getId(), log.getLevel(), log.getArea(), log.getMessage(),
						log.getCreatedAt().toInstant(ZoneOffset.UTC)))
				.toList();
	}
}
