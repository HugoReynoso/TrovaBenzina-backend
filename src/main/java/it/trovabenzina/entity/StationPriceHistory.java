package it.trovabenzina.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "station_price_history")
public class StationPriceHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "station_id", nullable = false)
	private Station station;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "fuel_type_id", nullable = false)
	private FuelType fuelType;

	@Column(nullable = false, precision = 8, scale = 3)
	private BigDecimal price;

	@Column(name = "self_service", nullable = false)
	private Boolean selfService;

	@Column(name = "communicated_at")
	private LocalDateTime communicatedAt;

	@Column(name = "imported_at", nullable = false)
	private LocalDateTime importedAt;

	@PrePersist
	void prePersist() {
		if (importedAt == null) {
			importedAt = LocalDateTime.now();
		}
	}
}
