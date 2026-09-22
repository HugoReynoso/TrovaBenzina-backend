package it.trovabenzina.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import it.trovabenzina.entity.StationPriceHistory;

public interface StationPriceHistoryRepository extends JpaRepository<StationPriceHistory, Long> {

	Optional<StationPriceHistory> findFirstByStationIdAndFuelTypeIdAndSelfServiceOrderByImportedAtDesc(Long stationId,
			Long fuelTypeId, Boolean selfService);

	boolean existsByStationIdAndFuelTypeIdAndSelfServiceAndPriceAndCommunicatedAt(Long stationId, Long fuelTypeId,
			Boolean selfService, BigDecimal price, LocalDateTime communicatedAt);
}
