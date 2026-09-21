package it.trovabenzina.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import it.trovabenzina.entity.FuelType;

public interface FuelTypeRepository extends JpaRepository<FuelType, Long> {

	Optional<FuelType> findByCodeIgnoreCase(String code);
}
