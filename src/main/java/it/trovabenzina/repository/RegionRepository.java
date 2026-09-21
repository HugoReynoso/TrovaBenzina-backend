package it.trovabenzina.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import it.trovabenzina.entity.Region;

public interface RegionRepository extends JpaRepository<Region, Long> {
}
