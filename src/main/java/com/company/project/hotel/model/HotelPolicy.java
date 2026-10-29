package com.company.project.hotel.model;

import com.company.project.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A paragraph of a hotel's policies (children, pets, deposit...), shown on the hotel page. Check-in/out times and the
 * minimum check-in age are columns of {@link Hotel}. Read-only for now: the hotel owner stories (O-HOTEL-*) add the
 * writes.
 */
@Entity
@Table(name = "hotel_policies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HotelPolicy extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "hotel_id", nullable = false)
	private Hotel hotel;

	@Enumerated(EnumType.STRING)
	@Column(name = "policy_type", nullable = false, length = 20)
	private HotelPolicyType type;

	@Column(nullable = false)
	private String description;

	@Column(name = "sort_order", nullable = false)
	private short sortOrder;

}
