package it.trovabenzina.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import it.trovabenzina.entity.PriceReport;

public interface PriceReportRepository extends JpaRepository<PriceReport, Long> {

	@EntityGraph(attributePaths = { "station", "fuelType" })
	List<PriceReport> findAllByOrderBySubmittedAtDesc();
}
