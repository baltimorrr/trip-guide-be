package com.company.project.wishlist.model;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A hotel a user saved (C-SRCH-08). The only entity without {@code BaseEntity}: its table keeps the schema's
 * composite key and has no id, version or {@code updated_at}. It is never built in Java; {@code WishlistRepository}
 * writes and reads it with JPQL statements only.
 */
@Entity
@Table(name = "wishlists")
@IdClass(WishlistEntry.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WishlistEntry {

	@Id
	@Column(name = "user_id")
	private Long userId;

	@Id
	@Column(name = "hotel_id")
	private Long hotelId;

	/** When the hotel was first saved; saving it again keeps this. */
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	/** Primary key: one entry per user and hotel. */
	public record Key(Long userId, Long hotelId) implements Serializable {
	}

}
