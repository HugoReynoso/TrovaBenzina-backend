package it.trovabenzina.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.trovabenzina.dto.CityResponseDto;
import it.trovabenzina.exception.CityNotFoundException;
import it.trovabenzina.mapper.GeographyMapper;
import it.trovabenzina.repository.CityRepository;

@Service
public class CityService {

	private final CityRepository cityRepository;

	public CityService(CityRepository cityRepository) {
		this.cityRepository = cityRepository;
	}

	@Transactional(readOnly = true)
	public List<CityResponseDto> findAll(Long provinceId) {
		if (provinceId == null) {
			return cityRepository.findAllByOrderByNameAsc().stream().map(GeographyMapper::toDto).toList();
		}
		return cityRepository.findByProvinceIdOrderByNameAsc(provinceId).stream().map(GeographyMapper::toDto).toList();
	}

	@Transactional(readOnly = true)
	public CityResponseDto findById(Long id) {
		return cityRepository.findById(id).map(GeographyMapper::toDto).orElseThrow(() -> new CityNotFoundException(id));
	}
}
