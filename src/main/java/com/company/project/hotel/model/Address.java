package com.company.project.hotel.model;

import java.math.BigDecimal;

import com.company.project.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Structured address of a hotel; the destination sync and search read its parts through norm(), the hotel page
 * shows them with the coordinates. Maps the columns read so far; the hotel owner stories add the rest (postal code,
 * Google place, ...).
 */
@Entity
@Table(name = "addresses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Address extends BaseEntity {

	@Column(nullable = false, length = 200)
	private String line1;

	@Column(length = 100)
	private String ward;

	@Column(length = 100)
	private String district;

	@Column(nullable = false, length = 100)
	private String city;

	@Column(name = "state_province", length = 100)
	private String stateProvince;

	@Column(name = "country_code", nullable = false, length = 2)
	private String countryCode;

	/** Both null when the location is not known. */
	@Column(precision = 9, scale = 6)
	private BigDecimal latitude;

	@Column(precision = 9, scale = 6)
	private BigDecimal longitude;

}
