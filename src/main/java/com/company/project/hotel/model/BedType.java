package com.company.project.hotel.model;

import com.company.project.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Reference data seeded by migration V6: single, double, queen, king, twin. Room types list their beds in
 * {@code room_type_beds} ({@link RoomType#getBeds()}), which the search filters read in native SQL.
 */
@Entity
@Table(name = "bed_types")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BedType extends BaseEntity {

	@Column(nullable = false, length = 20)
	private String code;

	@Column(nullable = false, length = 60)
	private String name;

}
