package com.company.project.rate.model;

import com.company.project.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * What cancelling costs, as {@link CancellationPolicyRule}s. A hotel's own policy or, without a hotel, a platform
 * template. Read-only for now: the owner stories (O-HOTEL-*, O-ROOM-02) add the hotel and the writes.
 */
@Entity
@Table(name = "cancellation_policies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CancellationPolicy extends BaseEntity {

	@Column(nullable = false, length = 150)
	private String name;

	@Column(name = "is_refundable", nullable = false)
	private boolean refundable;

}
