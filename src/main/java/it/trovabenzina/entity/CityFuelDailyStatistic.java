package it.trovabenzina.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "city_fuel_daily_statistics", uniqueConstraints = {
		@UniqueConstraint(name = "uk_city_fuel_stat_date", columnNames = { "city_id", "fuel_type_id", "date" })
})
public class CityFuelDailyStatistic {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "city_id", nullable = false)
	private City city;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "fuel_type_id", nullable = false)
	private FuelType fuelType;

	@Column(nullable = false)
	private LocalDate date;

	@Column(name = "average_price", nullable = false, precision = 8, scale = 3)
	private BigDecimal averagePrice;

	@Column(name = "minimum_price", nullable = false, precision = 8, scale = 3)
	private BigDecimal minimumPrice;

	@Column(name = "maximum_price", nullable = false, precision = 8, scale = 3)
	private BigDecimal maximumPrice;

	@Column(name = "station_count", nullable = false)
	private Integer stationCount;
}
