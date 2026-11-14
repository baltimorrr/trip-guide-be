package com.company.project.cart.dto;

/** Whether a cart line can be booked right now, worked out on every read (C-CART-02). */
public enum CartItemStatus {

	/** The rate sells the stay and enough rooms are free for the group's lines of this room type. */
	AVAILABLE,

	/** The rate still sells the stay, but fewer rooms are free than the group's lines of this room type need. */
	NOT_ENOUGH_ROOMS,

	/**
	 * The rate no longer sells the stay (sold out, a closed night, a minimum stay, the plan, room type or hotel no
	 * longer on sale) or the room type no longer holds the line's guests.
	 */
	UNAVAILABLE,

	/** Check-in is in the past; the line is deleted by the next expiry run (C-CART-04). */
	CHECK_IN_PASSED

}
