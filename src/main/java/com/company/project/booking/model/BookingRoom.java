package com.company.project.booking.model;

import java.util.ArrayList;
import java.util.List;

import com.company.project.cart.model.RoomGuests;
import com.company.project.common.model.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One room booked (three rooms are three rows), with what was sold copied from the room type and rate plan: a
 * later change to them doesn't change the booking. Its price is the sum of its {@link #nights}.
 */
@Entity
@Table(name = "booking_rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingRoom extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_id", nullable = false, updatable = false)
	private Booking booking;

	@Column(name = "room_type_id", nullable = false, updatable = false)
	private Long roomTypeId;

	@Column(name = "rate_plan_id", nullable = false, updatable = false)
	private Long ratePlanId;

	@Column(name = "room_name", nullable = false, length = 150)
	private String roomName;

	@Column(name = "board_type_code", nullable = false, length = 10)
	private String boardTypeCode;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "cancellation_policy", nullable = false)
	private CancellationTerms cancellationPolicy;

	@Column(nullable = false)
	private short adults;

	@Column(nullable = false)
	private short children;

	@Column(name = "child_ages", nullable = false)
	private Short[] childAges;

	@Column(name = "lead_guest_name", length = 100)
	private String leadGuestName;

	/** This room for the whole stay. */
	@Column(name = "total_minor", nullable = false)
	private long totalMinor;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private BookingRoomStatus status;

	@ElementCollection
	@CollectionTable(name = "booking_room_nights", joinColumns = @JoinColumn(name = "booking_room_id"))
	@OrderBy("stayDate")
	private List<BookingNight> nights = new ArrayList<>();

	public static BookingRoom create(long roomTypeId, long ratePlanId, String roomName, String boardTypeCode,
			CancellationTerms cancellationPolicy, RoomGuests guests, String leadGuestName, List<BookingNight> nights) {
		BookingRoom room = new BookingRoom();
		room.roomTypeId = roomTypeId;
		room.ratePlanId = ratePlanId;
		room.roomName = roomName;
		room.boardTypeCode = boardTypeCode;
		room.cancellationPolicy = cancellationPolicy;
		room.adults = (short) guests.adults();
		room.children = (short) guests.children();
		room.childAges = guests.childAges().stream().map(Integer::shortValue).toArray(Short[]::new);
		room.leadGuestName = leadGuestName == null || leadGuestName.isBlank() ? null : leadGuestName.trim();
		room.status = BookingRoomStatus.ACTIVE;
		room.nights = new ArrayList<>(nights);
		room.totalMinor = nights.stream().mapToLong(BookingNight::getPriceMinor).sum();
		return room;
	}

	public RoomGuests guests() {
		return RoomGuests.of(adults, childAges);
	}

	void attachTo(Booking booking) {
		this.booking = booking;
	}

}
