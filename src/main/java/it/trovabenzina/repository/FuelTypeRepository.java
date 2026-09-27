package it.trovabenzina.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import it.trovabenzina.entity.FuelType;

public interface FuelTypeRepository extends JpaRepository<FuelType, Long> {

	Optional<FuelType> findByCodeIgnoreCase(String code);

	@Query("""
			select ft
			from FuelType ft
			where upper(ft.code) in :codes
			""")
	List<FuelType> findByUpperCodeIn(@Param("codes") Collection<String> codes);
}
