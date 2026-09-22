package it.trovabenzina.mapper;

import it.trovabenzina.dto.CityResponseDto;
import it.trovabenzina.dto.ProvinceResponseDto;
import it.trovabenzina.dto.RegionResponseDto;
import it.trovabenzina.entity.City;
import it.trovabenzina.entity.Province;
import it.trovabenzina.entity.Region;

public final class GeographyMapper {

	private GeographyMapper() {
	}

	public static RegionResponseDto toDto(Region region) {
		return new RegionResponseDto(region.getId(), region.getName());
	}

	public static ProvinceResponseDto toDto(Province province) {
		Region region = province.getRegion();
		return new ProvinceResponseDto(province.getId(), province.getName(), province.getCode(), region.getId());
	}

	public static CityResponseDto toDto(City city) {
		Province province = city.getProvince();
		Region region = province.getRegion();
		return new CityResponseDto(city.getId(), city.getName(), city.getSlug(), province.getId(), province.getName(),
				region.getName(), city.getLatitude(), city.getLongitude());
	}
}
