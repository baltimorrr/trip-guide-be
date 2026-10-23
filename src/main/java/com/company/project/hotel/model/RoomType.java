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

/**
 * A kind of room a hotel sells, read by search to name offers. Room types are not created through JPA yet, so
 * this maps only what those queries read; the room story (O-ROOM-01) adds the rest with the writes.
 */
@Entity
@Table(name = "room_types")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomType extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "hotel_id", nullable = false)
	private Hotel hotel;

	@Column(nullable = false, length = 150)
	private String name;

}
