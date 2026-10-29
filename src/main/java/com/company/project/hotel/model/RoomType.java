package com.company.project.hotel.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.company.project.common.model.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A kind of room a hotel sells, read by search to name offers and by the hotel page's room list. Room types are not
 * created through JPA yet, so this maps only what those queries read; the room story (O-ROOM-01) adds the rest with
 * the writes. Availability and capacity checks run in the search's native SQL, not through this entity.
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

	private String description;

	@Column(name = "size_sqm", precision = 6, scale = 1)
	private BigDecimal sizeSqm;

	@Column(name = "max_adults", nullable = false)
	private short maxAdults;

	@Column(name = "max_children", nullable = false)
	private short maxChildren;

	@Column(name = "max_occupancy", nullable = false)
	private short maxOccupancy;

	@Column(name = "sort_order", nullable = false)
	private short sortOrder;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ListingStatus status;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	// The link tables are read only through JPQL joins on the hotel page, never loaded by search.
	@ElementCollection
	@CollectionTable(name = "room_type_beds", joinColumns = @JoinColumn(name = "room_type_id"))
	private List<RoomTypeBed> beds = new ArrayList<>();

	/** Room features: amenities with scope ROOM. */
	@ManyToMany
	@JoinTable(name = "room_type_amenities", joinColumns = @JoinColumn(name = "room_type_id"),
			inverseJoinColumns = @JoinColumn(name = "amenity_id"))
	private List<Amenity> features = new ArrayList<>();

}
