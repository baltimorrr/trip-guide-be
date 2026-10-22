package com.company.project.hotel.model;

import com.company.project.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Reference data seeded by migration V3: hotel, resort, apartment, villa, hostel. */
@Entity
@Table(name = "accommodation_types")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccommodationType extends BaseEntity {

	@Column(nullable = false, length = 30)
	private String code;

	@Column(nullable = false, length = 60)
	private String name;

}
