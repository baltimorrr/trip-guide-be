package com.company.project.tax.model;

import java.math.BigDecimal;
import java.time.LocalDate;

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
 * A tax of one country, either already inside room prices or added on top. Search only asks whether a country adds
 * any tax on top; checkout prices it with {@link TaxCalculator}. Admins write rules in A-CONF-02.
 */
@Entity
@Table(name = "tax_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaxRule extends BaseEntity {

	@Column(name = "country_code", nullable = false, length = 2)
	private String countryCode;

	@Column(nullable = false, length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "calc_type", nullable = false, length = 20)
	private TaxCalcType calcType;

	/** A percent for {@code PERCENT}, else an amount in major units of {@link #currency}. */
	@Column(nullable = false, precision = 12, scale = 4)
	private BigDecimal value;

	/** The fixed types' currency; null for {@code PERCENT}. */
	@Column(length = 3)
	private String currency;

	@Column(name = "is_included", nullable = false)
	private boolean included;

	/** {@code PERCENT} only: charged on the price after the booking's discount. */
	@Column(name = "applies_after_discount", nullable = false)
	private boolean appliesAfterDiscount;

	/** First night the rule applies to; null = no start limit. */
	@Column(name = "valid_from")
	private LocalDate validFrom;

	/** Last night the rule applies to (inclusive); null = no end limit. */
	@Column(name = "valid_to")
	private LocalDate validTo;

	@Column(name = "is_active", nullable = false)
	private boolean active;

}
