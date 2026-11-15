package com.company.project.booking.model;

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

/** A tax charged on a booking, copied from its tax rule so the booking keeps the rate it was charged. */
@Entity
@Table(name = "booking_taxes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingTax extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_id", nullable = false, updatable = false)
	private Booking booking;

	@Column(name = "tax_rule_id", updatable = false)
	private Long taxRuleId;

	@Column(nullable = false, length = 100)
	private String name;

	/** Already inside the room prices, so listed but not added to the total. */
	@Column(name = "is_included", nullable = false)
	private boolean included;

	@Column(name = "amount_minor", nullable = false)
	private long amountMinor;

	public static BookingTax create(Long taxRuleId, String name, boolean included, long amountMinor) {
		BookingTax tax = new BookingTax();
		tax.taxRuleId = taxRuleId;
		tax.name = name;
		tax.included = included;
		tax.amountMinor = amountMinor;
		return tax;
	}

	void attachTo(Booking booking) {
		this.booking = booking;
	}

}
