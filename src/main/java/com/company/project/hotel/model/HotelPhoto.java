package com.company.project.hotel.model;

import com.company.project.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A hotel photo ({@code roomTypeId} null) or a room photo. One hotel photo may be the cover. */
@Entity
@Table(name = "hotel_photos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HotelPhoto extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "hotel_id", nullable = false)
	private Hotel hotel;

	@Column(name = "room_type_id")
	private Long roomTypeId;

	@Column(nullable = false, length = 500)
	private String url;

	@Column(name = "is_cover", nullable = false)
	private boolean cover;

	@Column(name = "sort_order", nullable = false)
	private short sortOrder;

}
