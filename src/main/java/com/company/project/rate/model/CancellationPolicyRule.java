package com.company.project.rate.model;

import java.math.BigDecimal;

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
 * "Cancelling less than {@code hoursBeforeCheckIn} hours before check-in costs the penalty". The rule that applies
 * is the one with the smallest {@code hoursBeforeCheckIn} still greater than the hours left. Read-only: search and
 * the hotel page's cancellation timeline read it; refunds (C-PAY-05) will charge the same penalties.
 */
@Entity
@Table(name = "cancellation_policy_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CancellationPolicyRule extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "policy_id", nullable = false)
	private CancellationPolicy policy;

	@Column(name = "hours_before_check_in", nullable = false)
	private int hoursBeforeCheckIn;

	@Enumerated(EnumType.STRING)
	@Column(name = "penalty_type", nullable = false, length = 10)
	private PenaltyType penaltyType;

	@Column(name = "penalty_value", nullable = false, precision = 12, scale = 2)
	private BigDecimal penaltyValue;

}
