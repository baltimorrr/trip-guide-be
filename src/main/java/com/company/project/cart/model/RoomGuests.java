package com.company.project.cart.model;

import java.util.Arrays;
import java.util.List;

/**
 * The guests of one room of a cart line. Ages are kept sorted, so the same children in another order are the same
 * line ({@code cart_items_line_key} compares the stored array).
 */
public record RoomGuests(int adults, List<Integer> childAges) {

	public RoomGuests {
		childAges = childAges.stream().sorted().toList();
	}

	/** From the {@code smallint} columns, as JPA reads them. */
	public static RoomGuests of(short adults, Short[] childAges) {
		return new RoomGuests(adults, Arrays.stream(childAges).map(Short::intValue).toList());
	}

	public int children() {
		return childAges.size();
	}

}
