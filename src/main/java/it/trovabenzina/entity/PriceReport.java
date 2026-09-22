package it.trovabenzina.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "price_reports")
public class PriceReport {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "station_id", nullable = false)
	private Station station;

	@Column(name = "station_name", nullable = false, length = 255)
	private String stationName;

	@Column(length = 120)
	private String brand;

	@Column(name = "city_name", nullable = false, length = 120)
	private String cityName;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "fuel_type_id", nullable = false)
	private FuelType fuelType;

	@Column(nullable = false, precision = 8, scale = 3)
	private BigDecimal price;

	@Column(name = "self_service", nullable = false)
	private Boolean selfService;

	@Column(name = "reporter_name", length = 120)
	private String reporterName;

	@Column(name = "reporter_email", length = 180)
	private String reporterEmail;

	@Column(length = 500)
	private String note;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PriceReportStatus status = PriceReportStatus.PENDING;

	@Column(name = "submitted_at", nullable = false)
	private LocalDateTime submittedAt;

	@PrePersist
	void prePersist() {
		if (status == null) {
			status = PriceReportStatus.PENDING;
		}
		if (submittedAt == null) {
			submittedAt = LocalDateTime.now();
		}
	}
}
