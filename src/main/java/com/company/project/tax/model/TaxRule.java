package com.company.project.tax.model;

import java.time.LocalDate;

import com.company.project.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A tax of one country, either already inside room prices or added on top. Search only asks whether a country adds
 * any tax on top, so the amounts are not mapped yet; the tax story (A-CONF-02) and checkout (C-BOOK-02) add them.
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

	@Column(name = "is_included", nullable = false)
	private boolean included;

	/** First night the rule applies to; null = no start limit. */
	@Column(name = "valid_from")
	private LocalDate validFrom;

	/** Last night the rule applies to (inclusive); null = no end limit. */
	@Column(name = "valid_to")
	private LocalDate validTo;

	@Column(name = "is_active", nullable = false)
	private boolean active;

}
