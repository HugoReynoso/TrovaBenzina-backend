package it.trovabenzina.repository;

import java.util.Collection;
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
			select sp
			from StationPrice sp
			join fetch sp.station s
			join fetch sp.fuelType ft
			where s.id in :stationIds
			  and ft.id in :fuelTypeIds
			""")
	List<StationPrice> findCurrentByStationIdInAndFuelTypeIdIn(@Param("stationIds") Collection<Long> stationIds,
			@Param("fuelTypeIds") Collection<Long> fuelTypeIds);

	@Query("""
			select avg(sp.price), min(sp.price), max(sp.price), count(distinct sp.station.id), max(sp.communicatedAt)
			from StationPrice sp
			where sp.station.city.id = :cityId
			  and sp.fuelType.id = :fuelTypeId
			  and sp.station.active = true
			  and sp.price > 0
			""")
	Object[] calculateStatistics(@Param("cityId") Long cityId, @Param("fuelTypeId") Long fuelTypeId);
}
