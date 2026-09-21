package it.trovabenzina.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import it.trovabenzina.entity.City;

public interface CityRepository extends JpaRepository<City, Long> {

	@EntityGraph(attributePaths = { "province", "province.region" })
	List<City> findAllByOrderByNameAsc();

	@EntityGraph(attributePaths = { "province", "province.region" })
	List<City> findByProvinceIdOrderByNameAsc(Long provinceId);

	java.util.Optional<City> findFirstByNameIgnoreCaseAndProvinceCodeIgnoreCase(String name, String provinceCode);
}
