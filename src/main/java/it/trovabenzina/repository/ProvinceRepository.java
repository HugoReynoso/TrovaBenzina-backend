package it.trovabenzina.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import it.trovabenzina.entity.Province;

public interface ProvinceRepository extends JpaRepository<Province, Long> {

	@EntityGraph(attributePaths = "region")
	List<Province> findAllByOrderByNameAsc();

	@EntityGraph(attributePaths = "region")
	List<Province> findByRegionIdOrderByNameAsc(Long regionId);
}
