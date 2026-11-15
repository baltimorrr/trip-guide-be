package com.company.project.booking.services.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.company.project.booking.config.BookingProperties;
import com.company.project.booking.dto.BookerDetails;
import com.company.project.booking.dto.BookingRequest;
import com.company.project.booking.dto.BookingResponse;
import com.company.project.booking.dto.CheckoutQuote;
import com.company.project.booking.dto.CheckoutRequest;
import com.company.project.booking.dto.LeadGuests;
import com.company.project.booking.mapper.BookingMapper;
import com.company.project.booking.model.Booker;
import com.company.project.booking.model.Booking;
import com.company.project.booking.model.BookingNight;
import com.company.project.booking.model.BookingReference;
import com.company.project.booking.model.BookingRoom;
import com.company.project.booking.model.BookingStatus;
import com.company.project.booking.model.BookingTax;
import com.company.project.booking.model.CancellationTerms;
import com.company.project.booking.model.Charges;
import com.company.project.booking.model.PricedGroup;
import com.company.project.booking.model.PricedLine;
import com.company.project.booking.repository.BookingRepository;
import com.company.project.booking.repository.RoomInventoryRepository;
import com.company.project.booking.services.BookingService;
import com.company.project.cart.model.RoomGuests;
import com.company.project.cart.repository.CartItemRepository;
import com.company.project.cart.repository.CartLineView;
import com.company.project.cart.repository.CartRepository;
import com.company.project.common.dto.Money;
import com.company.project.common.exception.BusinessException;
import com.company.project.common.exception.ResourceNotFoundException;
import com.company.project.discount.model.AppliedDiscount;
import com.company.project.discount.model.DiscountUse;
import com.company.project.discount.services.DiscountService;
import com.company.project.hotel.repository.HotelRepository;
import com.company.project.hotel.repository.RoomRateView;
import com.company.project.hotel.repository.StayRequirement;
import com.company.project.rate.repository.CancellationRuleView;
import com.company.project.rate.repository.RateOfferView;
import com.company.project.rate.repository.RatePlanRepository;
import com.company.project.search.model.SearchCriteria;
import com.company.project.tax.model.TaxCalculator;
import com.company.project.tax.model.TaxLine;
import com.company.project.tax.repository.TaxRuleRepository;
import com.company.project.user.model.User;
import com.company.project.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingServiceImpl implements BookingService {

	private static final int EXPIRY_BATCH = 100;

	/** The bookings that count as the user's for a "first booking only" code: all but cancelled and expired ones. */
	private static final Set<BookingStatus> COUNTED = EnumSet.of(BookingStatus.PENDING, BookingStatus.CONFIRMED,
			BookingStatus.CHECKED_IN, BookingStatus.CHECKED_OUT, BookingStatus.NO_SHOW);

	private final CartRepository carts;

	private final CartItemRepository cartItems;

	private final HotelRepository hotels;

	private final RatePlanRepository ratePlans;

	private final TaxRuleRepository taxRules;

	private final UserRepository users;

	private final BookingRepository bookings;

	private final RoomInventoryRepository roomInventory;

	private final DiscountService discounts;

	private final BookingMapper bookingMapper;

	private final BookingProperties properties;

	@Override
	public CheckoutQuote quote(long userId, CheckoutRequest request) {
		PricedGroup group = price(userId, request.cartItemIds());
		Charges charges = charge(group, userId, request.discountCode(), false);
		User user = users.findById(userId).orElseThrow();
		return bookingMapper.toQuote(group, charges, new BookerDetails(user.getFullName(), user.getEmail(),
				user.getPhone(), user.getCountryCode()), Instant.now());
	}

	// Locks are taken in one order by every booking: the cart, the discount code, then the rooms by room type and
	// night. Two checkouts therefore wait for each other instead of deadlocking.
	@Override
	@Transactional
	public BookingResponse create(long userId, BookingRequest request) {
		carts.findByUserIdForUpdate(userId)
			.orElseThrow(() -> new ResourceNotFoundException("Cart item", request.cartItemIds().getFirst()));
		PricedGroup group = price(userId, request.cartItemIds());
		Map<Long, List<String>> leadGuests = leadGuests(request.leadGuests(), group);
		Charges charges = charge(group, userId, request.discountCode(), true);
		Money total = Money.ofMinor(charges.totalMinor(), group.currency());
		if (!total.currency().equals(request.expectedTotal().currency())
				|| total.amount().compareTo(request.expectedTotal().amount()) != 0) {
			throw new BusinessException(HttpStatus.CONFLICT, "The total is now %s %s. Check the new price and confirm it"
				.formatted(total.amount().toPlainString(), total.currency()));
		}
		hold(group);

		Instant now = Instant.now();
		BookerDetails booker = request.booker();
		Booking booking = Booking.create(newReference(), userId, group.hotel().id(), group.checkIn(), group.checkOut(),
				new Booker(booker.fullName(), booker.email(), booker.phone(), booker.countryCode()),
				request.guestName(), request.specialRequests(), request.estimatedArrival(), now,
				now.plus(properties.hold()), group.currency(), hotels.findCommissionRate(group.hotel().id()));
		for (PricedLine line : group.lines()) {
			List<String> names = leadGuests.getOrDefault(line.line().itemId(), List.of());
			for (int room = 0; room < line.line().quantity(); room++) {
				booking.addRoom(BookingRoom.create(line.line().roomTypeId(), line.line().ratePlanId(),
						line.offer().roomTypeName(), line.offer().boardCode(), terms(line), line.line().guests(),
						room < names.size() ? names.get(room) : null, nights(group, line)));
			}
		}
		if (charges.discount() != null) {
			booking.applyDiscount(charges.discount().discountCodeId(), charges.discountMinor());
		}
		charges.taxes().forEach(tax -> booking.addTax(BookingTax.create(tax.taxRuleId(), tax.name(), tax.included(),
				tax.amountMinor())));
		bookings.saveAndFlush(booking);
		if (charges.discount() != null) {
			discounts.redeem(charges.discount(), booking.getId(), userId);
		}
		cartItems.deleteAllByIdInBatch(group.lines().stream().map(line -> line.line().itemId()).toList());
		return bookingMapper.toBooking(booking, group, charges, now);
	}

	@Override
	@Transactional
	public int expireHolds() {
		List<Booking> expired = bookings.findExpiredHoldsForUpdate(Instant.now(), EXPIRY_BATCH);
		if (expired.isEmpty()) {
			return 0;
		}
		// Before any change: the status log reads it when the changes are flushed.
		bookings.setStatusReason("Payment not completed before the hold expired");
		List<Long> ids = expired.stream().map(Booking::getId).toList();
		roomInventory.release(ids);
		discounts.releaseUses(ids);
		expired.forEach(Booking::expire);
		bookings.flush();
		return expired.size();
	}

	/**
	 * Prices the user's cart lines {@code cartItemIds} by the cart's rules: the hotel page's room list for their stay
	 * and guests per room, with the group's lines of one room type sharing the rooms left. Throws when they aren't
	 * one bookable group.
	 */
	private PricedGroup price(long userId, List<Long> cartItemIds) {
		Set<Long> ids = new LinkedHashSet<>(cartItemIds);
		List<CartLineView> lines = cartItems.findLines(userId).stream().filter(line -> ids.contains(line.itemId()))
			.toList();
		if (lines.size() < ids.size()) {
			Set<Long> found = lines.stream().map(CartLineView::itemId).collect(Collectors.toSet());
			throw new ResourceNotFoundException("Cart item",
					ids.stream().filter(id -> !found.contains(id)).findFirst().orElseThrow());
		}
		CartLineView first = lines.getFirst();
		if (lines.stream().anyMatch(line -> !line.hotelId().equals(first.hotelId())
				|| !line.checkIn().equals(first.checkIn()) || !line.checkOut().equals(first.checkOut()))) {
			throw new BusinessException(HttpStatus.BAD_REQUEST,
					"A booking holds the rooms of one hotel for one date range");
		}
		if (first.checkIn().isBefore(SearchCriteria.today())) {
			throw new BusinessException(HttpStatus.CONFLICT, "Check-in for these rooms has passed");
		}

		Map<RoomGuests, Map<Long, RoomRateView>> rates = new HashMap<>();
		Set<Long> ratePlanIds = lines.stream().map(CartLineView::ratePlanId).collect(Collectors.toSet());
		Map<Long, RateOfferView> offers = ratePlans.findOfferDetails(ratePlanIds).stream()
			.collect(Collectors.toMap(RateOfferView::ratePlanId, Function.identity()));
		Map<Long, List<CancellationRuleView>> rules = ratePlans.findCancellationRules(ratePlanIds).stream()
			.collect(Collectors.groupingBy(CancellationRuleView::ratePlanId));
		List<PricedLine> priced = lines.stream().map(line -> {
			RoomRateView rate = rates.computeIfAbsent(line.guests(), guests -> roomRates(first, guests))
				.get(line.ratePlanId());
			RateOfferView offer = offers.get(line.ratePlanId());
			if (rate == null || !rate.fitsGuests()) {
				throw new BusinessException(HttpStatus.CONFLICT,
						"%s is no longer available for these dates".formatted(offer.roomTypeName()));
			}
			return new PricedLine(line, rate, offer, rules.getOrDefault(line.ratePlanId(), List.of()));
		}).toList();
		priced.stream()
			.collect(Collectors.groupingBy(line -> line.line().roomTypeId(), Collectors.toList()))
			.values()
			.forEach(sameRoomType -> {
				int needed = sameRoomType.stream().mapToInt(line -> line.line().quantity()).sum();
				PricedLine any = sameRoomType.getFirst();
				if (needed > any.rate().roomsLeft()) {
					throw new BusinessException(HttpStatus.CONFLICT, "Only %d rooms of %s are left for these dates"
						.formatted(any.rate().roomsLeft(), any.offer().roomTypeName()));
				}
			});
		return new PricedGroup(hotels.findCardsByIdIn(Set.of(first.hotelId())).getFirst(), first.checkIn(),
				first.checkOut(), first.hotelCurrency(), priced);
	}

	/**
	 * The subtotal, the code's discount (checked, or with {@code reserve} also locked for the booking's transaction),
	 * the hotel country's taxes and the total.
	 */
	private Charges charge(PricedGroup group, long userId, String code, boolean reserve) {
		long subtotal = group.subtotalMinor();
		AppliedDiscount discount = null;
		if (code != null && !code.isBlank()) {
			DiscountUse use = new DiscountUse(code, userId, group.hotel().id(), group.currency(), subtotal,
					!bookings.existsByUserIdAndStatusIn(userId, COUNTED));
			discount = reserve ? discounts.reserve(use) : discounts.check(use);
		}
		long discountMinor = discount == null ? 0 : discount.amountMinor();
		LocalDate lastNight = group.checkOut().minusDays(1);
		List<TaxLine> taxes = TaxCalculator.taxes(
				new TaxCalculator.Stay(group.currency(), group.nightPrices(), group.rooms(), group.guests(),
						discountMinor),
				taxRules.findApplicable(group.hotel().countryCode(), group.checkIn(), lastNight));
		long taxesOnTop = taxes.stream().filter(tax -> !tax.included()).mapToLong(TaxLine::amountMinor).sum();
		return new Charges(subtotal, discount, taxes, subtotal - discountMinor + taxesOnTop);
	}

	/** The lead guest names by cart line, each line naming at most its rooms. */
	private static Map<Long, List<String>> leadGuests(List<LeadGuests> leadGuests, PricedGroup group) {
		Map<Long, Integer> rooms = group.lines().stream()
			.collect(Collectors.toMap(line -> line.line().itemId(), line -> line.line().quantity()));
		Map<Long, List<String>> names = new HashMap<>();
		for (LeadGuests line : leadGuests) {
			Integer quantity = rooms.get(line.cartItemId());
			if (quantity == null) {
				throw new BusinessException(HttpStatus.BAD_REQUEST,
						"Lead guests name cart item %d, which isn't in this booking".formatted(line.cartItemId()));
			}
			if (line.names().size() > quantity) {
				throw new BusinessException(HttpStatus.BAD_REQUEST,
						"Cart item %d has %d rooms, so it takes at most %d lead guests".formatted(line.cartItemId(),
								quantity, quantity));
			}
			if (names.put(line.cartItemId(), line.names()) != null) {
				throw new BusinessException(HttpStatus.BAD_REQUEST,
						"Lead guests of cart item %d are given twice".formatted(line.cartItemId()));
			}
		}
		return names;
	}

	// Room types in id order, so concurrent bookings lock inventory rows in the same order.
	private void hold(PricedGroup group) {
		Map<Long, Integer> rooms = new TreeMap<>();
		Map<Long, String> names = new HashMap<>();
		for (PricedLine line : group.lines()) {
			rooms.merge(line.line().roomTypeId(), line.line().quantity(), Integer::sum);
			names.put(line.line().roomTypeId(), line.offer().roomTypeName());
		}
		rooms.forEach((roomTypeId, count) -> {
			if (!roomInventory.hold(roomTypeId, count, group.checkIn(), group.checkOut())) {
				throw new BusinessException(HttpStatus.CONFLICT,
						"The last rooms of %s were just booked. Go back to your cart to see what is left"
							.formatted(names.get(roomTypeId)));
			}
		});
	}

	private String newReference() {
		String reference = BookingReference.next();
		while (bookings.existsByReference(reference)) {
			reference = BookingReference.next();
		}
		return reference;
	}

	private static CancellationTerms terms(PricedLine line) {
		return new CancellationTerms(line.offer().policyName(), line.offer().refundable(), line.rules().stream()
			.map(rule -> new CancellationTerms.Rule(rule.hoursBeforeCheckIn(), rule.penaltyType(),
					rule.penaltyValue()))
			.toList());
	}

	private static List<BookingNight> nights(PricedGroup group, PricedLine line) {
		List<Long> prices = line.rate().nightPricesMinor();
		return IntStream.range(0, prices.size())
			.mapToObj(night -> new BookingNight(group.checkIn().plusDays(night), prices.get(night)))
			.toList();
	}

	private Map<Long, RoomRateView> roomRates(CartLineView stay, RoomGuests guests) {
		return hotels.findRoomRates(stay.hotelId(), new StayRequirement(stay.checkIn(), stay.checkOut(), 1,
				guests.adults(), guests.children())).stream()
			.collect(Collectors.toMap(RoomRateView::ratePlanId, Function.identity()));
	}

}
