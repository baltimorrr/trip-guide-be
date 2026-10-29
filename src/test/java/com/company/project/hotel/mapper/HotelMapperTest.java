package com.company.project.hotel.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.List;

import com.company.project.hotel.dto.HotelDetail;
import com.company.project.hotel.repository.HotelDetailView;
import com.company.project.hotel.repository.HotelFacilityView;
import org.junit.jupiter.api.Test;

class HotelMapperTest {

	private final HotelMapper mapper = new HotelMapper();

	@Test
	void formattedAddressSkipsMissingPartsAndLeavesOutTheProvince() {
		assertThat(HotelMapper.formatAddress(view("5 Trần Hưng Đạo", "Minh An", null, "Hội An", "VN", "Vietnam")))
			.isEqualTo("5 Trần Hưng Đạo, Minh An, Hội An, Vietnam");
		assertThat(HotelMapper.formatAddress(view(" 1 Main St ", "  ", "", "Town", "VN", "Vietnam")))
			.isEqualTo("1 Main St, Town, Vietnam");
	}

	@Test
	void formattedAddressUsesTheCodeForAnUnnamedCountry() {
		assertThat(HotelMapper.formatAddress(view("1 Main St", null, null, "Town", "QM", null)))
			.isEqualTo("1 Main St, Town, QM");
	}

	@Test
	void facilityGroupsKeepTheQueryOrder() {
		HotelDetail detail = mapper.toDetail(view("1 Main St", null, null, "Town", "VN", "Vietnam"), List.of(),
				List.of(new HotelFacilityView("General", "wifi", "Free Wi-Fi", null, true),
						new HotelFacilityView("General", "parking", "Parking", null, false),
						new HotelFacilityView("Wellness", "pool", "Swimming pool", "pool", true)),
				List.of());

		assertThat(detail.facilities()).extracting("name").containsExactly("General", "Wellness");
		assertThat(detail.facilities().getFirst().facilities()).extracting("code").containsExactly("wifi", "parking");
		assertThat(detail.facilities().getLast().facilities().getFirst().icon()).isEqualTo("pool");
	}

	private static HotelDetailView view(String line1, String ward, String district, String city, String countryCode,
			String countryName) {
		return new HotelDetailView(1L, "slug", "Hotel", "hotel", "Hotel", null, null, line1, ward, district, city,
				countryCode, countryName, null, null, null, null, null, null, 0, LocalTime.of(14, 0), null,
				LocalTime.of(12, 0), (short) 18);
	}

}
