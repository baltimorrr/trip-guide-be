package com.company.project.hotel.model;

import com.company.project.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Reference data seeded by migration V6: hotel facilities (wifi, pool...) and room features (balcony, sea view...).
 * Hotels and room types link to it through {@code hotel_amenities} ({@link Hotel#getFacilities()}) and
 * {@code room_type_amenities} ({@link RoomType#getFeatures()}); the search filters read those tables in native SQL.
 */
@Entity
@Table(name = "amenities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Amenity extends BaseEntity {

	@Column(nullable = false, length = 50)
	private String code;

	@Column(nullable = false, length = 100)
	private String name;

	/** Heading the amenity is listed under: General, Wellness, Bathroom... */
	@Column(name = "group_name", nullable = false, length = 60)
	private String groupName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private AmenityScope scope;

	@Column(length = 50)
	private String icon;

}
