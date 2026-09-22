package it.trovabenzina.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import it.trovabenzina.entity.Station;

public interface StationRepository extends JpaRepository<Station, Long> {

	Optional<Station> findByMimitId(String mimitId);

	@EntityGraph(attributePaths = { "city", "city.province", "city.province.region" })
	@Query("""
			select distinct s
			from Station s
			left join s.city c
			left join s.city.province p
			left join s.city.province.region r
			left join StationPrice sp on sp.station = s
			left join sp.fuelType ft
			where (:cityId is null or c.id = :cityId)
			  and (:fuelType is null or upper(ft.code) = upper(:fuelType))
			  and (:selfService is null or sp.selfService = :selfService)
			order by s.name asc
			""")
	List<Station> findStations(@Param("cityId") Long cityId, @Param("fuelType") String fuelType,
			@Param("selfService") Boolean selfService);

	@EntityGraph(attributePaths = { "city", "city.province", "city.province.region" })
	@Query("""
			select s
			from Station s
			left join s.city c
			where s.id = :id
			""")
	Optional<Station> findDetailsById(@Param("id") Long id);

	@Query("""
			select distinct s
			from Station s
			join fetch s.city c
			join fetch c.province p
			join fetch p.region r
			join StationPrice sp on sp.station = s
			join sp.fuelType ft
			where (:cityId is null or c.id = :cityId)
			  and upper(ft.code) = upper(:fuelType)
			  and (:selfService is null or sp.selfService = :selfService)
			order by sp.price asc
			""")
	List<Station> findCheapest(@Param("cityId") Long cityId, @Param("fuelType") String fuelType,
			@Param("selfService") Boolean selfService, org.springframework.data.domain.Pageable pageable);
}
