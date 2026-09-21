package it.trovabenzina.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import it.trovabenzina.entity.CityFuelDailyStatistic;

public interface CityFuelDailyStatisticRepository extends JpaRepository<CityFuelDailyStatistic, Long> {

	@EntityGraph(attributePaths = { "city", "fuelType" })
	Optional<CityFuelDailyStatistic> findFirstByCityIdAndFuelTypeCodeIgnoreCaseOrderByDateDesc(Long cityId,
			String fuelTypeCode);

	@EntityGraph(attributePaths = { "city", "fuelType" })
	List<CityFuelDailyStatistic> findByCityIdAndFuelTypeCodeIgnoreCaseAndDateBetweenOrderByDateAsc(Long cityId,
			String fuelTypeCode, LocalDate from, LocalDate to);

	Optional<CityFuelDailyStatistic> findByCityIdAndFuelTypeIdAndDate(Long cityId, Long fuelTypeId, LocalDate date);
}
