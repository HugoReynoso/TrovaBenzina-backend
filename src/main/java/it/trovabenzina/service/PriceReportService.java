package it.trovabenzina.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.trovabenzina.dto.PriceReportCreateDto;
import it.trovabenzina.dto.PriceReportResponseDto;
import it.trovabenzina.entity.FuelType;
import it.trovabenzina.entity.LogLevel;
import it.trovabenzina.entity.PriceReport;
import it.trovabenzina.entity.PriceReportStatus;
import it.trovabenzina.entity.Station;
import it.trovabenzina.entity.StationPrice;
import it.trovabenzina.exception.FuelTypeNotFoundException;
import it.trovabenzina.exception.ResourceNotFoundException;
import it.trovabenzina.exception.StationNotFoundException;
import it.trovabenzina.repository.FuelTypeRepository;
import it.trovabenzina.repository.PriceReportRepository;
import it.trovabenzina.repository.StationPriceRepository;
import it.trovabenzina.repository.StationRepository;

@Service
public class PriceReportService {

	private final PriceReportRepository priceReportRepository;
	private final StationRepository stationRepository;
	private final FuelTypeRepository fuelTypeRepository;
	private final StationPriceRepository stationPriceRepository;
	private final AdminLogService adminLogService;

	public PriceReportService(PriceReportRepository priceReportRepository, StationRepository stationRepository,
			FuelTypeRepository fuelTypeRepository, StationPriceRepository stationPriceRepository,
			AdminLogService adminLogService) {
		this.priceReportRepository = priceReportRepository;
		this.stationRepository = stationRepository;
		this.fuelTypeRepository = fuelTypeRepository;
		this.stationPriceRepository = stationPriceRepository;
		this.adminLogService = adminLogService;
	}

	@Transactional
	public PriceReportResponseDto create(PriceReportCreateDto dto) {
		Station station = stationRepository.findById(dto.stationId())
				.orElseThrow(() -> new StationNotFoundException(dto.stationId()));
		FuelType fuelType = fuelTypeRepository.findByCodeIgnoreCase(dto.fuelTypeCode())
				.orElseThrow(() -> new FuelTypeNotFoundException(dto.fuelTypeCode()));

		PriceReport report = new PriceReport();
		report.setStation(station);
		report.setStationName(dto.stationName());
		report.setBrand(dto.brand());
		report.setCityName(dto.cityName());
		report.setFuelType(fuelType);
		report.setPrice(dto.price());
		report.setSelfService(dto.selfService());
		report.setReporterName(dto.reporterName());
		report.setReporterEmail(dto.reporterEmail());
		report.setNote(dto.note());
		report.setStatus(PriceReportStatus.PENDING);
		PriceReport saved = priceReportRepository.save(report);
		adminLogService.log(LogLevel.INFO, "reports", "Nuova segnalazione prezzo ricevuta per " + saved.getStationName());
		return toDto(saved);
	}

	@Transactional(readOnly = true)
	public List<PriceReportResponseDto> findAll() {
		return priceReportRepository.findAllByOrderBySubmittedAtDesc().stream().map(this::toDto).toList();
	}

	@Transactional
	public PriceReportResponseDto approve(Long id) {
		PriceReport report = findReport(id);
		report.setStatus(PriceReportStatus.APPROVED);

		StationPrice price = stationPriceRepository
				.findByStationIdAndFuelTypeIdAndSelfService(report.getStation().getId(), report.getFuelType().getId(),
						report.getSelfService())
				.orElseGet(StationPrice::new);
		price.setStation(report.getStation());
		price.setFuelType(report.getFuelType());
		price.setPrice(report.getPrice());
		price.setSelfService(report.getSelfService());
		price.setCommunicatedAt(LocalDateTime.now());
		price.setImportedAt(LocalDateTime.now());
		stationPriceRepository.save(price);

		adminLogService.log(LogLevel.INFO, "reports", "Segnalazione approvata: " + report.getId());
		return toDto(report);
	}

	@Transactional
	public PriceReportResponseDto reject(Long id) {
		PriceReport report = findReport(id);
		report.setStatus(PriceReportStatus.REJECTED);
		adminLogService.log(LogLevel.WARNING, "reports", "Segnalazione rifiutata: " + report.getId());
		return toDto(report);
	}

	private PriceReport findReport(Long id) {
		return priceReportRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Price report not found with id " + id));
	}

	private PriceReportResponseDto toDto(PriceReport report) {
		return new PriceReportResponseDto(report.getId(), report.getStation().getId(), report.getStationName(),
				report.getBrand(), report.getCityName(), report.getFuelType().getCode(), report.getPrice(),
				report.getSelfService(), report.getReporterName(), report.getReporterEmail(), report.getNote(),
				report.getStatus(), report.getSubmittedAt().toInstant(ZoneOffset.UTC));
	}
}
