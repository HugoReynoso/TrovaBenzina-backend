package it.trovabenzina.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import it.trovabenzina.entity.Station;

public interface StationRepository extends JpaRepository<Station, Long> {

	Optional<Station> findByMimitId(String mimitId);

	@EntityGraph(attributePaths = { "city", "city.province", "city.province.region" })
	List<Station> findByMimitIdIn(Collection<String> mimitIds);

	@EntityGraph(attributePaths = { "city", "city.province", "city.province.region" })
	@Query("""
			select distinct s
			from Station s
			left join s.city c
			left join s.city.province p
			left join s.city.province.region r
			left join StationPrice sp on sp.station = s
			left join sp.fuelType ft
			where s.active = true
			  and (:cityId is null or c.id = :cityId)
			  and (:provinceId is null or p.id = :provinceId)
			  and (:fuelType is null or upper(ft.code) = upper(:fuelType))
			  and (:selfService is null or sp.selfService = :selfService)
			  and (:minLat is null or s.latitude >= :minLat)
			  and (:maxLat is null or s.latitude <= :maxLat)
			  and (:minLng is null or s.longitude >= :minLng)
			  and (:maxLng is null or s.longitude <= :maxLng)
			order by s.name asc
			""")
	List<Station> findStations(@Param("cityId") Long cityId, @Param("provinceId") Long provinceId,
			@Param("fuelType") String fuelType, @Param("selfService") Boolean selfService,
			@Param("minLat") Double minLat, @Param("maxLat") Double maxLat, @Param("minLng") Double minLng,
			@Param("maxLng") Double maxLng, Pageable pageable);

	@EntityGraph(attributePaths = { "city", "city.province", "city.province.region" })
	@Query("""
			select s
			from Station s
			left join s.city c
			where s.id = :id
			""")
	Optional<Station> findDetailsById(@Param("id") Long id);

	@Query("""
			select s
			from Station s
			join fetch s.city c
			join fetch c.province p
			join fetch p.region r
			join StationPrice sp on sp.station = s
			join sp.fuelType ft
			where s.active = true
			  and (:cityId is null or c.id = :cityId)
			  and (:provinceId is null or p.id = :provinceId)
			  and upper(ft.code) = upper(:fuelType)
			  and (:selfService is null or sp.selfService = :selfService)
			order by sp.price asc
			""")
	List<Station> findCheapest(@Param("cityId") Long cityId, @Param("provinceId") Long provinceId,
			@Param("fuelType") String fuelType, @Param("selfService") Boolean selfService, Pageable pageable);

	@EntityGraph(attributePaths = { "city", "city.province", "city.province.region" })
	@Query("""
			select distinct s
			from Station s
			left join StationPrice sp on sp.station = s
			left join sp.fuelType ft
			where s.active = true
			  and s.latitude is not null
			  and s.longitude is not null
			  and (:fuelType is null or upper(ft.code) = upper(:fuelType))
			  and (:selfService is null or sp.selfService = :selfService)
			""")
	List<Station> findNearbyCandidates(@Param("fuelType") String fuelType,
			@Param("selfService") Boolean selfService);

	@EntityGraph(attributePaths = { "city", "city.province", "city.province.region" })
	@Query("""
			select distinct s
			from Station s
			left join StationPrice sp on sp.station = s
			left join sp.fuelType ft
			where s.active = true
			  and s.latitude is not null
			  and s.longitude is not null
			  and lower(s.municipality) = lower(:municipality)
			  and upper(s.provinceCode) = upper(:provinceCode)
			  and (:fuelType is null or upper(ft.code) = upper(:fuelType))
			  and (:selfService is null or sp.selfService = :selfService)
			""")
	List<Station> findStationsByMunicipalityAndProvinceCode(@Param("municipality") String municipality,
			@Param("provinceCode") String provinceCode, @Param("fuelType") String fuelType,
			@Param("selfService") Boolean selfService);
}
