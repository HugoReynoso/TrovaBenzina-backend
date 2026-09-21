package it.trovabenzina.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import it.trovabenzina.entity.StationPrice;

public interface StationPriceRepository extends JpaRepository<StationPrice, Long> {

	Optional<StationPrice> findByStationIdAndFuelTypeIdAndSelfService(Long stationId, Long fuelTypeId,
			Boolean selfService);

	@EntityGraph(attributePaths = { "fuelType" })
	List<StationPrice> findByStationIdIn(List<Long> stationIds);

	@Query("""
			select avg(sp.price), min(sp.price), max(sp.price), count(distinct sp.station.id)
			from StationPrice sp
			where sp.station.city.id = :cityId
			  and sp.fuelType.id = :fuelTypeId
			""")
	Object[] calculateStatistics(@Param("cityId") Long cityId, @Param("fuelTypeId") Long fuelTypeId);
}
