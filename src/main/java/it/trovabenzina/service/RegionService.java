package it.trovabenzina.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.trovabenzina.dto.RegionResponseDto;
import it.trovabenzina.mapper.GeographyMapper;
import it.trovabenzina.repository.RegionRepository;

@Service
public class RegionService {

	private final RegionRepository regionRepository;

	public RegionService(RegionRepository regionRepository) {
		this.regionRepository = regionRepository;
	}

	@Transactional(readOnly = true)
	public List<RegionResponseDto> findAll() {
		return regionRepository.findAll().stream().sorted(Comparator.comparing(region -> region.getName().toLowerCase()))
				.map(GeographyMapper::toDto).toList();
	}
}
