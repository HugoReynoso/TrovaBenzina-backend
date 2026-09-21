package it.trovabenzina.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.trovabenzina.dto.ProvinceResponseDto;
import it.trovabenzina.mapper.GeographyMapper;
import it.trovabenzina.repository.ProvinceRepository;

@Service
public class ProvinceService {

	private final ProvinceRepository provinceRepository;

	public ProvinceService(ProvinceRepository provinceRepository) {
		this.provinceRepository = provinceRepository;
	}

	@Transactional(readOnly = true)
	public List<ProvinceResponseDto> findAll(Long regionId) {
		if (regionId == null) {
			return provinceRepository.findAllByOrderByNameAsc().stream().map(GeographyMapper::toDto).toList();
		}
		return provinceRepository.findByRegionIdOrderByNameAsc(regionId).stream().map(GeographyMapper::toDto).toList();
	}
}
