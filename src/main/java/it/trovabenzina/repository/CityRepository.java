package it.trovabenzina.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import it.trovabenzina.entity.City;

public interface CityRepository extends JpaRepository<City, Long> {

	@EntityGraph(attributePaths = { "province", "province.region" })
	List<City> findAllByOrderByNameAsc();

	@EntityGraph(attributePaths = { "province", "province.region" })
	List<City> findByProvinceIdOrderByNameAsc(Long provinceId);

	@EntityGraph(attributePaths = { "province", "province.region" })
	@Query("""
			select c
			from City c
			join c.province p
			where upper(c.name) in :names
			  and upper(p.code) in :provinceCodes
			""")
	List<City> findByNamesAndProvinceCodes(@Param("names") Collection<String> names,
			@Param("provinceCodes") Collection<String> provinceCodes);

	java.util.Optional<City> findFirstByNameIgnoreCaseAndProvinceCodeIgnoreCase(String name, String provinceCode);

	@EntityGraph(attributePaths = { "province", "province.region" })
	@Query("""
			select c
			from City c
			join c.province p
			where lower(c.name) = lower(:name)
			  and (:province is null
			       or lower(p.code) = lower(:province)
			       or lower(p.name) = lower(:province))
			order by c.id asc
			""")
	List<City> findByNameAndOptionalProvince(@Param("name") String name, @Param("province") String province);
}
