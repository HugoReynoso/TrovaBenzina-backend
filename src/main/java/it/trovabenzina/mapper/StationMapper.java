package it.trovabenzina.mapper;

import java.time.ZoneOffset;
import java.util.List;

import it.trovabenzina.dto.StationPriceDto;
import it.trovabenzina.dto.StationResponseDto;
import it.trovabenzina.entity.City;
import it.trovabenzina.entity.Province;
import it.trovabenzina.entity.Region;
import it.trovabenzina.entity.Station;
import it.trovabenzina.entity.StationPrice;

public final class StationMapper {

	private StationMapper() {
	}

	public static StationPriceDto toPriceDto(StationPrice price) {
		return new StationPriceDto(price.getFuelType().getCode(), price.getFuelType().getName(), price.getPrice(),
				price.getSelfService(),
				price.getCommunicatedAt() == null ? null : price.getCommunicatedAt().toInstant(ZoneOffset.UTC));
	}

	public static StationResponseDto toDto(Station station, List<StationPrice> prices) {
		City city = station.getCity();
		Province province = city != null ? city.getProvince() : null;
		Region region = province != null ? province.getRegion() : null;
		List<StationPriceDto> priceDtos = prices.stream().map(StationMapper::toPriceDto).toList();

		return new StationResponseDto(station.getId(), station.getMimitId(), station.getName(), station.getBrand(),
				station.getAddress(), station.getLatitude(), station.getLongitude(), city != null ? city.getId() : null,
				city != null ? city.getName() : null, province != null ? province.getName() : null,
				region != null ? region.getName() : null, null, priceDtos);
	}
}
