package com.company.project.cart.services.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.company.project.cart.config.CartProperties;
import com.company.project.cart.dto.CartGroup;
import com.company.project.cart.dto.CartItemRequest;
import com.company.project.cart.dto.CartItemResponse;
import com.company.project.cart.dto.CartItemStatus;
import com.company.project.cart.dto.CartItemUpdateRequest;
import com.company.project.cart.dto.CartMergeRequest;
import com.company.project.cart.dto.CartMergeResponse;
import com.company.project.cart.dto.CartResponse;
import com.company.project.cart.dto.SkippedCartItem;
import com.company.project.cart.mapper.CartMapper;
import com.company.project.cart.model.Cart;
import com.company.project.cart.model.CartItem;
import com.company.project.cart.model.RoomGuests;
import com.company.project.cart.repository.CartItemRepository;
import com.company.project.cart.repository.CartLineView;
import com.company.project.cart.repository.CartRepository;
import com.company.project.cart.services.CartService;
import com.company.project.common.exception.BusinessException;
import com.company.project.common.exception.ResourceNotFoundException;
import com.company.project.hotel.repository.HotelCardView;
import com.company.project.hotel.repository.HotelRepository;
import com.company.project.hotel.repository.RoomPhotoView;
import com.company.project.hotel.repository.RoomRateView;
import com.company.project.hotel.repository.RoomTypeRepository;
import com.company.project.hotel.repository.StayRequirement;
import com.company.project.rate.repository.CancellationRuleView;
import com.company.project.rate.repository.RateOfferView;
import com.company.project.rate.repository.RatePlanRepository;
import com.company.project.rate.repository.RatePlanSellerView;
import com.company.project.search.config.SearchProperties;
import com.company.project.search.model.SearchCriteria;
import com.company.project.tax.repository.TaxRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartServiceImpl implements CartService {

	private static final Comparator<CartGroup> GROUP_ORDER = Comparator.comparing(CartGroup::checkIn)
		.thenComparing(group -> group.hotel().name())
		.thenComparing(group -> group.hotel().id());

	private final CartRepository carts;

	private final CartItemRepository cartItems;

	private final HotelRepository hotels;

	private final RoomTypeRepository roomTypes;

	private final RatePlanRepository ratePlans;

	private final TaxRuleRepository taxRules;

	private final CartMapper cartMapper;

	private final CartProperties properties;

	private final SearchProperties limits;

	// Prices, availability and rooms left come from the hotel page's room list, called once per stay and guests: the
	// cart never re-implements what can be sold or at what price. JPQL then loads the names, terms, photos and cards.
	@Override
	public CartResponse cart(long userId) {
		List<CartLineView> lines = cartItems.findLines(userId);
		if (lines.isEmpty()) {
			return new CartResponse(List.of());
		}
		LocalDate today = SearchCriteria.today();
		Map<PriceKey, Map<Long, RoomRateView>> rates = new HashMap<>();
		for (CartLineView line : lines) {
			if (!line.checkIn().isBefore(today)) {
				rates.computeIfAbsent(PriceKey.of(line), this::roomRates);
			}
		}
		Map<CartLineView, CartItemStatus> statuses = statuses(lines, rates, today);

		Set<Long> ratePlanIds = lines.stream().map(CartLineView::ratePlanId).collect(Collectors.toSet());
		Set<Long> hotelIds = lines.stream().map(CartLineView::hotelId).collect(Collectors.toSet());
		Map<Long, RateOfferView> offers = ratePlans.findOfferDetails(ratePlanIds).stream()
			.collect(Collectors.toMap(RateOfferView::ratePlanId, Function.identity()));
		Map<Long, List<CancellationRuleView>> rules = ratePlans.findCancellationRules(ratePlanIds).stream()
			.collect(Collectors.groupingBy(CancellationRuleView::ratePlanId));
		Map<Long, String> photos = roomTypes.findPhotos(lines.stream().map(CartLineView::roomTypeId).toList()).stream()
			.collect(Collectors.toMap(RoomPhotoView::roomTypeId, RoomPhotoView::url, (first, next) -> first));
		Map<Long, HotelCardView> cards = hotels.findCardsByIdIn(hotelIds).stream()
			.collect(Collectors.toMap(HotelCardView::id, Function.identity()));
		Set<Long> listed = new HashSet<>(hotels.findListedIds(hotelIds));
		// One "now" for every line, so they all agree on what is still free to cancel.
		Instant now = Instant.now();

		Map<StayKey, List<CartLineView>> groups = lines.stream()
			.collect(Collectors.groupingBy(StayKey::of, LinkedHashMap::new, Collectors.toList()));
		return new CartResponse(groups.entrySet().stream().map(group -> {
			StayKey stay = group.getKey();
			List<CartItemResponse> items = group.getValue().stream().map(line -> {
				RoomRateView rate = rates.getOrDefault(PriceKey.of(line), Map.of()).get(line.ratePlanId());
				return cartMapper.toItem(line, statuses.get(line), rate, offers.get(line.ratePlanId()),
						rules.getOrDefault(line.ratePlanId(), List.of()), photos.get(line.roomTypeId()), now);
			}).toList();
			HotelCardView card = cards.get(stay.hotelId());
			return cartMapper.toGroup(card, listed.contains(stay.hotelId()), stay.checkIn(), stay.checkOut(),
					taxesIncluded(card.countryCode(), stay), items);
		}).sorted(GROUP_ORDER).toList());
	}

	@Override
	@Transactional
	public CartResponse add(long userId, CartItemRequest request) {
		RoomGuests guests = new RoomGuests(request.adults(), request.childAges());
		checkStay(request.checkIn(), request.checkOut(), guests, request.quantity());
		RatePlanSellerView seller = seller(request.ratePlanId());
		Cart cart = lockOrCreate(userId);
		List<Line> lines = lines(cart);
		Optional<Line> existing = lines.stream()
			.filter(line -> line.item().isLine(seller.ratePlanId(), request.checkIn(), request.checkOut(), guests))
			.findFirst();
		if (existing.isEmpty() && lines.size() >= properties.maxItems()) {
			throw cartFull();
		}
		// Adding a line that is already in the cart is picking it again: the rooms add up.
		int quantity = request.quantity() + existing.map(line -> (int) line.item().getQuantity()).orElse(0);
		RoomRateView rate = checkLine(lines, seller, request.checkIn(), request.checkOut(), guests, quantity,
				existing.map(line -> Set.of(line.id())).orElse(Set.of()));
		if (existing.isPresent()) {
			CartItem item = existing.get().item();
			item.changeQuantity(quantity);
			item.rebasePrice(rate.stayPriceMinor(), seller.hotelCurrency());
		}
		else {
			cartItems.save(CartItem.create(cart, ratePlans.getReferenceById(seller.ratePlanId()), request.checkIn(),
					request.checkOut(), guests, quantity, rate.stayPriceMinor(), seller.hotelCurrency()));
		}
		cartItems.flush();
		return cart(userId);
	}

	@Override
	@Transactional
	public CartResponse update(long userId, long itemId, CartItemUpdateRequest request) {
		Cart cart = carts.findByUserIdForUpdate(userId).orElseThrow(() -> itemNotFound(itemId));
		List<Line> lines = lines(cart);
		Line line = lines.stream().filter(l -> l.id() == itemId).findFirst().orElseThrow(() -> itemNotFound(itemId));
		CartItem item = line.item();
		RoomGuests guests = item.guests();
		RatePlanSellerView seller = seller(item.getRatePlan().getId());
		// New dates may be another line's: the rooms then move into that line.
		Optional<Line> target = lines.stream()
			.filter(other -> other != line
					&& other.item().isLine(seller.ratePlanId(), request.checkIn(), request.checkOut(), guests))
			.findFirst();
		int quantity = request.quantity() + target.map(other -> (int) other.item().getQuantity()).orElse(0);
		RoomRateView rate = checkLine(lines, seller, request.checkIn(), request.checkOut(), guests, quantity,
				target.map(other -> Set.of(itemId, other.id())).orElse(Set.of(itemId)));
		if (target.isPresent()) {
			target.get().item().changeQuantity(quantity);
			target.get().item().rebasePrice(rate.stayPriceMinor(), seller.hotelCurrency());
			cartItems.delete(item);
		}
		else if (!item.getCheckIn().equals(request.checkIn()) || !item.getCheckOut().equals(request.checkOut())) {
			item.changeStay(request.checkIn(), request.checkOut(), rate.stayPriceMinor(), seller.hotelCurrency());
			item.changeQuantity(quantity);
		}
		else {
			item.changeQuantity(quantity);
		}
		cartItems.flush();
		return cart(userId);
	}

	@Override
	@Transactional
	public void remove(long userId, long itemId) {
		cartItems.deleteByIdAndCartUserId(itemId, userId);
	}

	@Override
	@Transactional
	public CartMergeResponse merge(long userId, CartMergeRequest request) {
		List<CartItemRequest> items = request.items();
		if (items.size() > properties.maxItems()) {
			throw new BusinessException(HttpStatus.BAD_REQUEST,
					"A cart holds at most %d room choices".formatted(properties.maxItems()));
		}
		if (items.isEmpty()) {
			return new CartMergeResponse(cart(userId), List.of());
		}
		Cart cart = lockOrCreate(userId);
		List<Line> lines = lines(cart);
		List<SkippedCartItem> skipped = new ArrayList<>();
		for (int index = 0; index < items.size(); index++) {
			// Every check throws before anything is written, so a skipped item leaves the cart as it was.
			try {
				mergeItem(cart, lines, items.get(index));
			}
			catch (BusinessException ex) {
				skipped.add(new SkippedCartItem(index, ex.getMessage()));
			}
		}
		cartItems.flush();
		return new CartMergeResponse(cart(userId), skipped);
	}

	@Override
	@Transactional
	public int removeExpiredItems() {
		return cartItems.deleteCheckedInBefore(SearchCriteria.today());
	}

	// The same pick made on two devices, or a merge sent twice: the larger quantity wins, and a line already in the
	// account keeps its price at add. Lines merged earlier in the call count in the rooms check.
	private void mergeItem(Cart cart, List<Line> lines, CartItemRequest request) {
		RoomGuests guests = new RoomGuests(request.adults(), request.childAges());
		checkStay(request.checkIn(), request.checkOut(), guests, request.quantity());
		RatePlanSellerView seller = seller(request.ratePlanId());
		Optional<Line> existing = lines.stream()
			.filter(line -> line.item().isLine(seller.ratePlanId(), request.checkIn(), request.checkOut(), guests))
			.findFirst();
		if (existing.isPresent()) {
			CartItem item = existing.get().item();
			if (request.quantity() > item.getQuantity()) {
				checkLine(lines, seller, request.checkIn(), request.checkOut(), guests, request.quantity(),
						Set.of(item.getId()));
				item.changeQuantity(request.quantity());
			}
			return;
		}
		if (lines.size() >= properties.maxItems()) {
			throw cartFull();
		}
		RoomRateView rate = checkLine(lines, seller, request.checkIn(), request.checkOut(), guests, request.quantity(),
				Set.of());
		CartItem item = cartItems.save(CartItem.create(cart, ratePlans.getReferenceById(seller.ratePlanId()),
				request.checkIn(), request.checkOut(), guests, request.quantity(), rate.stayPriceMinor(),
				seller.hotelCurrency()));
		lines.add(new Line(item, seller.hotelId(), seller.roomTypeId()));
	}

	/**
	 * Checks that {@code quantity} rooms of the seller's rate can be booked for the stay with these guests in each,
	 * next to the cart's other {@code lines} (those not in {@code excludeIds}), and returns the room list's row for
	 * the rate. A group of lines (one hotel, one date range) is one booking, so its lines of one room type share the
	 * rooms left.
	 */
	private RoomRateView checkLine(List<Line> lines, RatePlanSellerView seller, LocalDate checkIn, LocalDate checkOut,
			RoomGuests guests, int quantity, Set<Long> excludeIds) {
		checkStay(checkIn, checkOut, guests, quantity);
		RoomRateView rate = roomRates(new PriceKey(seller.hotelId(), checkIn, checkOut, guests.adults(),
				guests.children())).get(seller.ratePlanId());
		if (rate == null) {
			throw new BusinessException(HttpStatus.CONFLICT, "This rate can't be booked for these dates");
		}
		if (!rate.fitsGuests()) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "This room can't hold these guests");
		}
		int needed = quantity + lines.stream()
			.filter(line -> !excludeIds.contains(line.id()) && line.hotelId() == seller.hotelId()
					&& line.roomTypeId() == seller.roomTypeId() && line.item().getCheckIn().equals(checkIn)
					&& line.item().getCheckOut().equals(checkOut))
			.mapToInt(line -> line.item().getQuantity())
			.sum();
		if (needed > rate.roomsLeft()) {
			throw new BusinessException(HttpStatus.CONFLICT,
					"Only %d rooms of this type are left for these dates".formatted(rate.roomsLeft()));
		}
		return rate;
	}

	/**
	 * Checks the stay and guests by the search's rules, for {@code quantity} rooms each holding {@code guests}: 400
	 * for a past, too distant or too long stay, or too many rooms or guests.
	 */
	private void checkStay(LocalDate checkIn, LocalDate checkOut, RoomGuests guests, int quantity) {
		SearchCriteria.of(checkIn, checkOut, quantity, guests.adults() * quantity,
				Collections.nCopies(quantity, guests.childAges()).stream().flatMap(List::stream).toList(),
				SearchCriteria.today(), limits);
	}

	private Map<Long, RoomRateView> roomRates(PriceKey key) {
		return hotels.findRoomRates(key.hotelId(), new StayRequirement(key.checkIn(), key.checkOut(), 1, key.adults(),
				key.children())).stream().collect(Collectors.toMap(RoomRateView::ratePlanId, Function.identity()));
	}

	// A line is booked when its rate sells the stay, its room type holds the guests and the group's lines of that
	// room type fit in the rooms left.
	private static Map<CartLineView, CartItemStatus> statuses(List<CartLineView> lines,
			Map<PriceKey, Map<Long, RoomRateView>> rates, LocalDate today) {
		Map<CartLineView, RoomRateView> sellable = new HashMap<>();
		Map<CartLineView, CartItemStatus> statuses = new HashMap<>();
		for (CartLineView line : lines) {
			if (line.checkIn().isBefore(today)) {
				statuses.put(line, CartItemStatus.CHECK_IN_PASSED);
				continue;
			}
			RoomRateView rate = rates.get(PriceKey.of(line)).get(line.ratePlanId());
			if (rate == null || !rate.fitsGuests()) {
				statuses.put(line, CartItemStatus.UNAVAILABLE);
			}
			else {
				sellable.put(line, rate);
			}
		}
		Map<RoomsKey, Integer> needed = sellable.keySet().stream()
			.collect(Collectors.groupingBy(RoomsKey::of, Collectors.summingInt(CartLineView::quantity)));
		sellable.forEach((line, rate) -> statuses.put(line, needed.get(RoomsKey.of(line)) > rate.roomsLeft()
				? CartItemStatus.NOT_ENOUGH_ROOMS : CartItemStatus.AVAILABLE));
		return statuses;
	}

	private boolean taxesIncluded(String countryCode, StayKey stay) {
		return !taxRules.findCountriesWithTaxesOnTop(Set.of(countryCode), stay.checkIn(), stay.checkOut().minusDays(1))
			.contains(countryCode);
	}

	private RatePlanSellerView seller(long ratePlanId) {
		return ratePlans.findSeller(ratePlanId).orElseThrow(() -> new ResourceNotFoundException("Rate plan", ratePlanId));
	}

	private BusinessException cartFull() {
		return new BusinessException(HttpStatus.CONFLICT,
				"The cart can hold at most %d room choices".formatted(properties.maxItems()));
	}

	private static ResourceNotFoundException itemNotFound(long itemId) {
		return new ResourceNotFoundException("Cart item", itemId);
	}

	// The first room ever added creates the cart. Two first adds at once: the second fails on the unique user_id (409).
	private Cart lockOrCreate(long userId) {
		return carts.findByUserIdForUpdate(userId).orElseGet(() -> carts.saveAndFlush(Cart.create(userId)));
	}

	private List<Line> lines(Cart cart) {
		return cartItems.findInCart(cart.getId()).stream().map(Line::of).collect(Collectors.toList());
	}

	/** A line of the locked cart, with the hotel and room type its rate plan sells. */
	private record Line(CartItem item, long hotelId, long roomTypeId) {

		static Line of(CartItem item) {
			return new Line(item, item.getRatePlan().getRoomType().getHotel().getId(),
					item.getRatePlan().getRoomType().getId());
		}

		Long id() {
			return item.getId();
		}

	}

	/** What one call of the room list prices: a hotel, a stay and the guests of one room. */
	private record PriceKey(long hotelId, LocalDate checkIn, LocalDate checkOut, int adults, int children) {

		static PriceKey of(CartLineView line) {
			return new PriceKey(line.hotelId(), line.checkIn(), line.checkOut(), line.guests().adults(),
					line.guests().children());
		}

	}

	/** A group: one hotel and one date range, checked out as one booking. */
	private record StayKey(long hotelId, LocalDate checkIn, LocalDate checkOut) {

		static StayKey of(CartLineView line) {
			return new StayKey(line.hotelId(), line.checkIn(), line.checkOut());
		}

	}

	/** The rooms one group's lines take of one room type. */
	private record RoomsKey(long hotelId, LocalDate checkIn, LocalDate checkOut, long roomTypeId) {

		static RoomsKey of(CartLineView line) {
			return new RoomsKey(line.hotelId(), line.checkIn(), line.checkOut(), line.roomTypeId());
		}

	}

}
