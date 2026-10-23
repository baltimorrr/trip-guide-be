package com.company.project.rate.model;

import com.company.project.common.model.BaseEntity;
import com.company.project.hotel.model.RoomType;
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
 * One way of selling a room type: board, cancellation policy and price. Search prices plans in native SQL
 * ({@code night_price()}) and reads only their names and terms through this entity, so prices, minimum stay and
 * status are not mapped yet; the rate plan story (O-ROOM-02) adds them with the writes.
 */
@Entity
@Table(name = "rate_plans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RatePlan extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "room_type_id", nullable = false)
	private RoomType roomType;

	@Column(nullable = false, length = 150)
	private String name;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "board_type_id", nullable = false)
	private BoardType boardType;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cancellation_policy_id", nullable = false)
	private CancellationPolicy cancellationPolicy;

}
