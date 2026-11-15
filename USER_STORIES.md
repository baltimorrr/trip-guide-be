# Hotel Booking Platform – User Stories

Last updated: 13 Nov 2026 · matches database design `hotel_booking.dbml` (54 tables)

## Changes in this version

Updated to match the decisions taken during the database design. Changed stories are marked **(updated)**.

| Decision | Stories affected |
|---|---|
| Booking statuses are Pending → Confirmed → Checked-in → Checked-out, plus Cancelled, Expired, No-show; the system blocks invalid status changes | C-MYBK-01, C-MYBK-05, O-BOOK-03, booking lifecycle |
| Physical room numbers (101, 102…) are assigned at check-in; one room can never be given to two overlapping stays | O-ROOM-01, O-BOOK-03 |
| Hotel address is a structured record (street, ward, district, city, province, country, coordinates), one per hotel | O-HOTEL-01, C-SRCH-06, C-HOTEL-01 |
| Destinations are synced from hotel addresses (country, city, district, ward); admins add images, featured order and display names | C-HOME-01, C-HOME-03, A-CONT-01, A-CONT-02 |
| Search history is not stored; "last search" lives in the browser; recommendations stay "Later" | C-HOME-02, C-HOME-04 |
| Location suggestions match the place name and tolerate half-typed words and small typos; typing a place with its city or country comes in Phase 2 | C-HOME-01, C-HOME-07 |
| Taxes are configured per country only; hotel service charges are included in the room price | C-BOOK-02, A-CONF-02 |
| One partner (business) per hotel; many users can manage a partner | A-USER-02, A-PAY-02 |
| Season and weekend prices plus single-date overrides | O-ROOM-03 |
| Sign-in uses a short-lived access token and a refresh token stored per device | C-AUTH-02, C-AUTH-05 |
| Sold-out hotels are hidden from results; a multi-room search is priced as the cheapest mix of room types | C-SRCH-01 |
| The cart holds rooms from several hotels and dates; each checkout books one hotel for one date range | C-CART-01 |

## Overview

The platform has a client site for travellers and an admin site shared by hotel owners and platform admins. Each story below has an ID, the story itself, acceptance criteria and a priority. Stories marked **[Added]** were not in the original requirements and fill gaps found during review.

| Actor | Side | Description |
| --- | --- | --- |
| Guest | Client | Visitor who is not signed in; can search and browse |
| Customer | Client | Signed-in user; can book, pay and see their bookings |
| Hotel owner | Admin | Manages their own hotel(s), rooms, prices and bookings |
| Admin | Admin | Platform operator; approves hotel and room changes, manages users and settings |
| System | Both | Scheduled jobs, emails, payment webhooks |

**Priority legend:** MVP = first release · Phase 2 = soon after launch · Later = explicitly deferred (e.g. recommendations).

**Status legend:** ✅ Implemented = the API meets every acceptance criterion (screens are not part of this repo) · 🟡 Partly implemented = what is left is listed under the story. Stories with no mark are not implemented yet.

**Story format:** As a *[actor]*, I want *[goal]* so that *[benefit]*.

## 1. Client – Account and authentication

1. **C-AUTH-01 Sign up with email (MVP) 🟡 Partly implemented** — As a guest, I want to create an account with my email and password so that I can book and track my stays.
   - Required fields: full name, email, password, confirm password; phone optional.
   - Email must be unique; password has a minimum strength rule (e.g. 8+ characters, letter and number).
   - A verification email is sent; the account is active after the link is clicked.
   - Clear error messages for duplicate email or weak password.
   - Left: send the email. The verification link is only written to the server log until an email service is chosen.
2. **C-AUTH-02 Sign in with email (MVP) (updated) ✅ Implemented** — As a customer, I want to sign in with email and password so that I can access my bookings.
   - Wrong credentials show a generic error (no hint whether the email exists).
   - Account is temporarily locked after repeated failed attempts (e.g. 5 in 15 minutes).
   - Sign-in returns a short-lived access token (about 15 minutes) and a refresh token; the refresh token is stored hashed, one session per device.
   - "Remember me" keeps the session (refresh token) for a longer period.
3. **C-AUTH-03 Sign in with Google (MVP)** — As a guest, I want to sign in with my Google account so that I don't need a new password.
   - First Google sign-in creates an account using the Google name and email.
   - If an email account already exists with the same address, the accounts are linked rather than duplicated.
4. **C-AUTH-04 Forgot / reset password [Added] (MVP) 🟡 Partly implemented** — As a customer, I want to reset my password by email so that I can recover my account.
   - Reset link expires (e.g. after 30 minutes) and can be used once.
   - Left: send the email. The reset link is only written to the server log until an email service is chosen.
5. **C-AUTH-05 Sign out [Added] (MVP) (updated) ✅ Implemented** — As a customer, I want to sign out so that my account is safe on shared devices.
   - Sign out revokes the session of this device; "Sign out of all devices" revokes every session.
6. **C-AUTH-06 Manage profile [Added] (MVP) ✅ Implemented** — As a customer, I want to edit my name, phone, nationality and preferred currency so that checkout is pre-filled.
   - Saved details pre-fill the guest information form at checkout (C-BOOK-05).
   - Customer can change password (email accounts only).
   - The checkout quote returns the saved name, email, phone and country as the booker (C-BOOK-05).
7. **C-AUTH-07 Delete account [Added] (Phase 2)** — As a customer, I want to request deletion of my account and personal data so that my privacy is respected.
   - Booking records needed for accounting are kept but anonymised; wishlist and recently viewed hotels are deleted.
8. **C-AUTH-08 Book as guest decision [Added]** — Decide whether a guest can book without an account. These stories assume sign-in is required before checkout; search, hotel detail and cart are open to guests.

## 2. Client – Homepage and search

1. **C-HOME-01 Search hotels (MVP) (updated) ✅ Implemented** — As a guest, I want to search by destination, hotel name or area, check-in and check-out dates, number of rooms and number of guests so that I only see hotels that can host my stay.
   - The location field autocompletes countries, cities, districts, wards and hotel names as I type; accents and case are ignored ("da nang" finds "Đà Nẵng").
   - A place is suggested when its name is close to what I typed, so a half-typed word ("hai ch") or a small typo ("hai chua") still finds "Hải Châu". The closest name comes first, then the place with more hotels.
   - Each place suggestion shows where it is, e.g. "Thạch Thang — Hải Châu, Đà Nẵng, Vietnam".
   - Only the place's own name is matched; typing it together with its city or country comes later (C-HOME-07).
   - Only places that have at least one active hotel are suggested.
   - Check-in cannot be in the past; check-out must be after check-in; a maximum stay length applies (e.g. 30 nights).
   - Guests are split into adults and children; child ages are asked because they affect price and room capacity.
   - Rooms: 1 to N; guests must be at least equal to rooms.
   - Submitting opens the search results page with the criteria in the URL so the search can be shared or bookmarked.
2. **C-HOME-02 Remember last search [Added] (MVP) (updated) ✅ Implemented** — As a guest, I want my last search to be pre-filled when I return so that I don't retype it.
   - Stored in the browser only; search history is not saved on the server.
3. **C-HOME-03 Popular destinations (MVP) (updated) ✅ Implemented** — As a guest, I want to see a section of the most popular destinations with photos and a few hotels each so that I can get ideas quickly.
   - Clicking a destination runs a search for it with default dates (e.g. tomorrow, 1 night) and 2 adults.
   - Destinations are created automatically from hotel addresses; the admin chooses which ones are featured, their order and image (A-CONT-01).
4. **C-HOME-04 Personalised recommendations (Later) (updated)** — As a signed-in customer, I want hotel recommendations based on my bookings, wishlist and recently viewed hotels so that I find relevant stays faster.
   - Only shown when signed in and enough history exists; otherwise falls back to popular hotels.
   - Search history is not stored for now; if needed later it goes to an analytics store, not the booking database.
5. **C-HOME-05 Recently viewed hotels [Added] (Phase 2)** — As a guest, I want to see hotels I viewed recently so that I can return to them.
   - Signed-in users: stored on the server, newest first, last 50 hotels kept; viewing a hotel again moves it to the top.
   - Guests: kept in the browser and merged into the account after sign-in.
   - Customer can remove one hotel or clear the whole list.
6. **C-HOME-06 Language selection [Added] (Phase 2)** — As a guest, I want to choose the site language (e.g. English, Vietnamese) so that I can read content comfortably.
7. **C-HOME-07 Find a place by its area [Added] (Phase 2)** — As a guest, I want to type a place together with the bigger places around it (e.g. "hai chau da nang" or "da nang vietnam") so that I can pick the right one when several places share a name.
   - A place also matches on the district, city, province and country it lies in, not only on its own name.
   - Half-typed words and small typos are tolerated as in C-HOME-01.

## 3. Client – Search results, filters, currency and map

1. **C-SRCH-01 Hotel result list (MVP) ✅ Implemented** — As a guest, I want a list of available hotels matching my search, each showing its cheapest available room, so that I can compare options.
   - Each card shows: photos (carousel), hotel name, star rating, Google rating, user rating and review count, distance from city centre, board type, cancellation policy (e.g. "Free cancellation until 12 Oct"), number of nights, and total price for the stay.
   - The price shown is the lowest room that fits the requested guests and rooms for the selected dates; it states whether taxes are included.
   - Hotels with no availability for the dates are hidden (decided 23 Oct 2026: not shown as "Sold out").
   - Result count is shown (e.g. "124 hotels in Da Nang").
   - Results are paginated or load more on scroll.
   - Clicking a card opens the hotel detail page with the same search criteria.
   - For a multi-room search the price is the cheapest mix: rooms come from the cheapest room type first, and the card lists each room type used.
   - The card states whether taxes are included; the tax amounts are shown in the checkout breakdown (C-BOOK-02). The detail page itself is C-HOTEL-01/04.
2. **C-SRCH-02 Filter results (MVP) ✅ Implemented** — As a guest, I want to filter results so that I only see hotels that match my needs.
   - Filters: hotel name, star rating, Google rating, user rating, board type (room only, breakfast, half board, full board, all-inclusive), cancellation policy (free / non-refundable), services and facilities, bed type, accommodation type (hotel, resort, apartment, villa, hostel), room features, price range, distance from centre.
   - Each filter option shows how many hotels match it.
   - Several filters can be combined; active filters show as chips that can be removed one by one or cleared together.
   - Filters are kept in the URL so they survive refresh and sharing.
3. **C-SRCH-03 Sort results [Added] (MVP) 🟡 Partly implemented** — As a guest, I want to sort results by recommended, lowest price, highest rating or nearest to centre so that the best options come first.
   - Sorting by user rating requires a minimum number of reviews so a single 10/10 review does not top the list.
   - Left: the "recommended" sort (decided 23 Oct 2026: postponed until it has a definition). Lowest price, highest rating and nearest to centre work; until currency conversion (C-SRCH-05), the price sort compares one currency and lists hotels selling in another one last.
4. **C-SRCH-04 Modify search [Added] (MVP)** — As a guest, I want to change destination, dates, rooms or guests from the results page without going back to the homepage.
5. **C-SRCH-05 Change currency (MVP)** — As a guest, I want to choose a display currency so that I understand prices in my own money.
   - Currency selector in the header; choice is saved for the session and in the profile for signed-in users.
   - Prices are converted using daily exchange rates from a rate provider; the rate update time is stored.
   - Checkout shows which currency the card will actually be charged in (see open question on charging currency).
6. **C-SRCH-06 Map view (MVP) (updated)** — As a guest, I want to see search results on Google Maps so that I can choose by location.
   - Hotel coordinates come from Google Places and are stored on the hotel's address.
   - Each pin shows the price; clicking a pin highlights the hotel card and opens a small preview.
   - Moving the map can optionally refresh results for the visible area ("Search this area").
   - Map and list stay in sync with the active filters.
7. **C-SRCH-07 No results state [Added] (MVP)** — As a guest, I want helpful suggestions when nothing matches (e.g. remove a filter, nearby dates, nearby areas) so that I don't hit a dead end.
8. **C-SRCH-08 Wishlist / favourites [Added] (Phase 2) ✅ Implemented** — As a customer, I want to save hotels to a wishlist so that I can compare and book later.
   - Saving is idempotent (saving twice keeps one entry); hearts on result cards show which hotels are saved.
   - Hearts come from the wishlist (decided 28 Oct 2026: the wishlist endpoint returns the saved hotels as cards, and the client marks result cards from them; search results don't say what is saved).
   - A saved hotel that stops being listed stays in the wishlist, marked unavailable (decided 28 Oct 2026), and can still be removed.
   - The wishlist shows no prices, since they depend on dates and guests; the customer opens a saved hotel with their last search.

## 4. Client – Hotel detail

1. **C-HOTEL-01 Hotel overview (MVP) (updated) ✅ Implemented** — As a guest, I want to see the hotel's photo gallery, description, star rating, address, Google rating, user rating and location on a map so that I can judge the hotel.
   - Gallery opens full screen and can be browsed by category (rooms, pool, lobby…).
   - The address is shown in one consistent format built from its parts (street, ward, district, city, country).
   - Map shows the hotel and distance to the city centre.
   - The country in the address is the country destination's name, like the search suggestions; the province is not part of the one-line address.
2. **C-HOTEL-02 Facilities (MVP) ✅ Implemented** — As a guest, I want a grouped list of facilities (e.g. Wi-Fi, pool, parking, airport shuttle) so that I know what is included.
   - Grouped under the amenity headings (General, Wellness…); each facility says whether it is free.
3. **C-HOTEL-03 Hotel policies (MVP) ✅ Implemented** — As a guest, I want to see check-in and check-out times and hotel policies so that I'm not surprised on arrival.
   - Includes: check-in/check-out times, minimum check-in age, ID or deposit required, children and extra bed policy, pet policy, smoking policy, payment methods accepted at the property.
   - The times and minimum age are hotel columns; the other policies are free-text paragraphs by type, entered with SQL until the owner stories (O-HOTEL-*) add a write API.
4. **C-HOTEL-04 Room list (MVP) ✅ Implemented** — As a guest, I want to see all rooms available for my dates so that I can pick the right one.
   - Each room option shows: room name, photos, size, bed type, max guests, room features, board type, cancellation policy, rooms left (e.g. "Only 2 left"), price per night and total price for the stay.
   - The same room type can appear several times with different board types or cancellation policies (rate plans), each with its own price.
   - Customer selects a quantity per room option, limited by the number of rooms left.
   - Rooms that cannot fit the guest count are marked or hidden. Decided 28 Oct 2026: marked (`fitsGuests: false`) and listed after the rooms that fit, so a guest can still mix room types; fitting uses the search's even split of guests per room.
   - Availability and prices use the search's own rules, so for one room the cheapest fitting rate is the offer on the search card. Sold-out room types and rates that can't sell the stay are not listed.
5. **C-HOTEL-05 Change dates on detail page [Added] (MVP)** — As a guest, I want to change dates or guests on the hotel page so that room availability and prices refresh without a new search.
   - API ready: the room list takes the stay as query parameters, so the page calls it again with the new dates or guests. The client part is left.
6. **C-HOTEL-06 Cancellation policy detail [Added] (MVP) ✅ Implemented** — As a guest, I want to see the exact cancellation deadline and penalty for each rate so that I know what I'll pay if plans change.
   - Each rate shows the free-cancellation deadline (the same as on the search card) and every penalty after it with its start, end and amount for one room, in the hotel's time zone.
   - Until refunds (C-MYBK-03) decide otherwise, a fixed penalty is per room and a penalty in nights charges a fraction of a night pro rata.
7. **C-HOTEL-07 Reviews (Phase 2) ✅ Implemented (reading)** — As a guest, I want to read reviews and scores from customers who stayed there so that I can trust the rating.
   - Only customers with a completed stay can write a review (C-MYBK-05).
   - Reviews show score, date, traveller type and text; can be sorted by newest or score.
   - Decided 28 Oct 2026: reviews keep the database design's one review per booking; the link to bookings is added with the bookings table, so the demo data has no reviews and the list stays empty until C-MYBK-05 lets customers write them. Only published reviews are shown, without the reviewer's name; the hotel's rating summary is still the stored one.
8. **C-HOTEL-08 Nearby places [Added] (Later)** — As a guest, I want to see nearby attractions, airports and transport with distances so that I can plan my trip.

## 5. Client – Cart and checkout

1. **C-CART-01 Add rooms to cart (MVP) (updated) ✅ Implemented** — As a guest, I want to add rooms from one or more hotels to my cart so that I can book them later.
   - Cart item stores: hotel, room option (room type + board + cancellation rate), quantity, dates and guests per room.
   - A cart can hold rooms from several hotels and for different dates: adding a room keeps the items already in the cart (decided 13 Nov 2026). The cart groups its items by hotel and date range.
   - A checkout books one group: the rooms of one hotel for one date range, which become one booking (C-BOOK-01) with one payment. The client starts checkout from a group, and the API rejects a checkout with rooms from more than one hotel or for different dates. The other groups stay in the cart, and each one is checked out on its own.
   - Guest carts are kept in the browser and merged into the account on sign-in.
   - Decided 13 Nov 2026: the server keeps carts for signed-in customers only. The browser sends its cart once after sign-in. A room already in the account's cart keeps the larger of the two quantities, so the same choice made on two devices isn't doubled. Adding a room that is already in the cart adds the quantities up.
   - Checkout (C-BOOK-01) takes the items of one group and rejects items of several hotels or dates.
2. **C-CART-02 Live cart prices (MVP) ✅ Implemented** — As a customer, I want the cart to always show the current price and availability so that I'm not misled.
   - Prices are not locked when added; they are re-calculated each time the cart is opened and again at checkout.
   - If a price changed since adding, the item shows the old and new price with a notice.
   - If a room is no longer available, it is marked unavailable and cannot be checked out.
   - The cart uses the hotel page's prices and availability, so they always match. An item can also be marked as having too few rooms left for its group, or as past its check-in date. The price check at checkout itself belongs to C-BOOK-04.
3. **C-CART-03 Edit cart (MVP) ✅ Implemented** — As a customer, I want to change quantity, remove items or change dates in the cart so that I can adjust my booking.
   - A new quantity or new dates are checked like a new room. Changing only the quantity keeps the "price changed" notice; new dates take today's price. New dates that match another item of the same rate and guests join the two.
4. **C-CART-04 Cart expiry [Added] (Phase 2) ✅ Implemented** — As the system, I want to clear cart items whose check-in date has passed so that the cart stays valid.
   - An hourly job deletes them, and until it runs the cart already shows them as past check-in.
5. **C-BOOK-01 Multi-room booking (MVP) ✅ Implemented** — As a customer, I want to book several rooms (same or different types) of one hotel in a single booking so that my group stays together with one payment.
   - One booking = one hotel, one date range, one or more room lines.
   - Each room line can carry its own guest count and lead guest name.
   - Decided 14 Nov 2026: booking needs an account; the customer books a group of their cart, and the booked items leave the cart when the booking is created. Each room booked is stored on its own, with its guests, lead guest name, nightly prices and the cancellation policy it was sold with.
6. **C-BOOK-02 Price breakdown (MVP) (updated) 🟡 Partly implemented** — As a customer, I want to see a clear price breakdown before paying so that I know what I'm paying for.
   - Shows: room subtotal per line (nights × rate × quantity), discount, taxes by type (e.g. VAT), and grand total.
   - Tax rules are configured per country (A-CONF-02) and can be included in the price or added on top.
   - Hotel service charges are not shown as a separate line: they are included in the room price set by the owner.
   - Total is shown in the display currency and in the charge currency if they differ.
   - Decided 14 Nov 2026: a booking is charged in the hotel's currency. Taxes are calculated per rule for the nights it covers; included taxes are listed with their amount but not added to the total.
   - Left: the total in the customer's display currency, which needs exchange rates (C-SRCH-05).
7. **C-BOOK-03 Apply discount code (MVP) ✅ Implemented** — As a customer, I want to apply a discount code so that I get the promised reduction.
   - Code is validated for: active dates, minimum spend, applicable hotels, usage limit (total and per user), first booking only, etc.
   - Clear message when invalid or expired; only one code per booking unless configured otherwise.
   - Tax is calculated on the discounted price by default (configurable per tax rule).
   - Codes match in any case, and each reason a code is refused has its own message. A code's money amounts (fixed value, minimum spend, cap) are in its currency, so it applies only to bookings in that currency. A use is counted from the booking; an unpaid booking that expires gives it back. Codes are created by the admin story (A-DISC-01).
8. **C-BOOK-04 Re-check before payment (MVP) ✅ Implemented** — As the system, I want to re-check availability and price right before payment so that I never sell a room that is gone.
   - Rooms are held for a short time (e.g. 15 minutes) once checkout starts; the hold is released if payment is not completed.
   - If price or availability changed, the customer must confirm the new total.
   - Two customers can never book the last room at the same time.
   - Decided 14 Nov 2026: the hold starts when the customer submits the guest details and the Pending booking is created (every booking row needs them), not when the breakdown is first shown. The booking request carries the total the customer saw; if the total changed, nothing is booked and the customer confirms the new one. Until payment exists (C-PAY-01), every booking expires when its hold ends.
9. **C-BOOK-05 Guest information (MVP) ✅ Implemented** — As a customer, I want to enter the guest details so that the hotel knows who is arriving.
   - Booker: full name, email, phone, country. Pre-filled from the profile.
   - Option "I'm booking for someone else" with the main guest's name.
   - Optional special requests (e.g. late check-in, high floor), clearly marked as not guaranteed.
   - Must accept terms, privacy policy and the rate's cancellation policy before paying.
   - One acceptance covers the terms, the privacy policy and the cancellation policies shown with the breakdown; its time is stored. Phone and country are required even when the profile lacks them.
10. **C-BOOK-06 Estimated arrival time [Added] (Phase 2) ✅ Implemented** — As a customer, I want to give my estimated arrival time so that the hotel can prepare.
   - Optional, in the hotel's local time.

## 6. Client – Payment with Stripe

1. **C-PAY-01 Pay by card via Stripe (MVP)** — As a customer, I want to pay securely by card through Stripe so that my booking is confirmed straight away.
   - Card entry uses Stripe Checkout or Stripe Elements; card data never touches our servers.
   - 3D Secure / SCA is supported when the bank requires it.
   - The booking is created as **Pending** before payment and becomes **Confirmed** (shown as "Success") only when the Stripe webhook confirms payment — not from the browser redirect alone.
   - Webhook handling is idempotent so a repeated event never creates a duplicate booking or charge.
2. **C-PAY-02 Payment failure (MVP)** — As a customer, I want a clear message and a retry option when payment fails so that I can complete my booking.
   - Booking stays Pending while the room hold lasts; after the hold expires it becomes **Expired** and rooms are released.
3. **C-PAY-03 Booking confirmation [Added] (MVP)** — As a customer, I want a confirmation page and email with the booking number so that I have proof of my reservation.
   - Email contains: booking reference, hotel name and address, dates, rooms, guests, board type, cancellation policy, price breakdown and hotel contact.
   - The hotel owner is notified of the new booking at the same time (O-BOOK-02).
4. **C-PAY-04 Invoice / receipt [Added] (Phase 2)** — As a customer, I want to download a receipt or VAT invoice so that I can claim expenses.
5. **C-PAY-05 Refund on cancellation [Added] (MVP)** — As a customer, I want refunds processed automatically to my card according to the cancellation policy so that I don't need to chase support.
   - Refund amount = amount paid − cancellation penalty; issued via the Stripe Refund API.
   - Refund status (pending, succeeded, failed) is shown on the booking.
6. **C-PAY-06 Other payment methods [Added] (Later)** — As a customer, I want to pay with wallets (Apple Pay, Google Pay) or local methods supported by Stripe so that paying is easier.

## 7. Client – My bookings

1. **C-MYBK-01 Booking list (MVP) (updated)** — As a customer, I want to see all my bookings grouped by status so that I can keep track of my trips.
   - Tabs or filter: Upcoming (Confirmed), Pending, Completed (Checked-out), Cancelled.
   - Each item shows: booking reference, hotel name and photo, dates, rooms, total paid and status.
   - Sorted by check-in date, newest first.
2. **C-MYBK-02 Booking detail (MVP)** — As a customer, I want to open a booking and see every detail so that I have everything I need for the stay.
   - Shows rooms, guests, board type, policies, price breakdown, payment and refund status, hotel contact and map.
3. **C-MYBK-03 Cancel booking [Added] (MVP)** — As a customer, I want to cancel a booking myself so that I don't need to contact the hotel.
   - Before confirming, shows the penalty and the refund amount based on the policy and current time.
   - Non-refundable rates can still be cancelled but show a refund of 0.
   - After cancelling: status becomes Cancelled, rooms return to inventory, refund starts (C-PAY-05), emails go to customer and owner.
   - A checked-in booking can no longer be cancelled.
   - Decide: can a single room in a multi-room booking be cancelled, or only the whole booking?
4. **C-MYBK-04 Resend confirmation [Added] (MVP)** — As a customer, I want to resend the confirmation email so that I can find it again.
5. **C-MYBK-05 Write a review [Added] (Phase 2) (updated)** — As a customer, I want to rate and review a hotel after check-out so that other travellers benefit.
   - Available only for Checked-out bookings, within a window (e.g. 30 days after check-out), once per booking.
   - Publishing or hiding a review updates the hotel's average score and review count.
6. **C-MYBK-06 Modify booking [Added] (Later)** — As a customer, I want to change dates or guest names on a booking so that I don't need to cancel and rebook.
7. **C-MYBK-07 Reminder emails [Added] (Phase 2)** — As a customer, I want a reminder a few days before check-in and a review request after check-out.

**Booking status lifecycle (updated, enforced by the database):**

```text
Pending ──► Confirmed ──► Checked-in ──► Checked-out
   │            ├──► Cancelled
   │            └──► No-show
   ├──► Cancelled
   └──► Expired (hold timed out or payment failed)
```

- Pending = created, awaiting payment; Confirmed = paid (shown to customers as "Success").
- Any other change (e.g. Pending → Checked-in, Checked-in → Cancelled) is refused.
- Every status change is recorded with time, who made it and the reason.

## 8. Hotel owner

**Account and onboarding**

1. **O-ACC-01 Owner registration [Added] (MVP)** — As a hotel owner, I want to register as a partner (or be invited by an admin) so that I can list my hotel.
   - Owner provides business name, contact, tax ID and supporting documents; account stays inactive until an admin approves it (A-USER-02).
2. **O-ACC-02 Owner sign in and password reset [Added] (MVP)** — As a hotel owner, I want to sign in to the admin site and reset my password so that I can manage my property securely.
3. **O-ACC-03 Staff accounts [Added] (Phase 2)** — As a hotel owner, I want to add staff users with limited permissions (e.g. bookings only) so that my team can help without full access.

**Hotel data**

4. **O-HOTEL-01 Manage hotel profile (MVP) (updated)** — As a hotel owner, I want to create and edit my hotel's name, description, address, location, star rating, accommodation type, photos, facilities, services and policies so that guests see accurate information.
   - Address is picked with Google Places autocomplete and stored as structured fields (street, ward, district, city, province, postal code, country) with coordinates; each hotel has exactly one address.
   - Star rating is the hotel's official classification (1–5); it is different from the guest review score.
   - Photos: upload, reorder, set cover, tag by category; size and format limits.
   - Policies: check-in/out times, children, pets, deposit, cancellation policy templates.
   - Changes are saved as a draft and go live only after admin approval (A-APPR-01).
5. **O-HOTEL-02 See approval status (MVP)** — As a hotel owner, I want to see whether each change is draft, pending, approved or rejected (with the reason) so that I know what is live.

**Rooms, rates and availability**

6. **O-ROOM-01 Manage room types (MVP) (updated)** — As a hotel owner, I want to add and edit room types (name, size, bed type, max adults and children, features, photos, total number of rooms) so that guests can book them.
   - New room types and changes to their description need admin approval before appearing in search.
   - The owner also lists the physical rooms of each type (room numbers such as 101, 102, floor); a room number is unique within the hotel and can be marked out of service.
7. **O-ROOM-02 Manage rate plans [Added] (MVP)** — As a hotel owner, I want to set rate plans per room type (board type, cancellation policy, base price) so that I can offer several options for the same room.
   - The base price includes the hotel's own service charge.
8. **O-ROOM-03 Availability and price calendar [Added] (MVP) (updated)** — As a hotel owner, I want to set rooms available and price per date so that I can handle seasons and weekends.
   - Season and weekend price rules for a date range and weekdays (e.g. +20% on Fri–Sat, a fixed price for the summer season); the highest-priority rule wins when rules overlap.
   - Single-date price overrides (e.g. New Year's Eve) win over rules.
   - Close a date (stop-sell) or set minimum nights.
   - Daily price and inventory updates go live directly without admin approval, because they change often.
   - Inventory can never be set below rooms already booked for that date.
9. **O-ROOM-04 Promotions [Added] (Phase 2)** — As a hotel owner, I want to create hotel-level promotions (early bird, last minute, long stay) so that I can fill empty rooms.

**Bookings**

10. **O-BOOK-01 Booking list (MVP)** — As a hotel owner, I want to see and filter all bookings for my hotel by date, status and booking reference so that I can prepare for arrivals.
    - Views: today's arrivals, departures, in-house, upcoming, cancelled.
    - Export to CSV/Excel.
11. **O-BOOK-02 New booking and cancellation notifications [Added] (MVP)** — As a hotel owner, I want an email and in-app alert for each new, changed or cancelled booking so that I never miss one.
12. **O-BOOK-03 Booking detail and status update (MVP) (updated)** — As a hotel owner, I want to open a booking, see guest details and special requests, and mark it as checked-in, checked-out or no-show so that records match reality.
    - At check-in the front desk assigns a physical room of the booked room type to each booked room; the system lists only free rooms and refuses a room already given to an overlapping stay.
    - A guest checking out and the next guest checking in on the same day can use the same room.
    - Only valid status changes are allowed (see the booking status lifecycle).
13. **O-BOOK-04 Cancel a booking as owner [Added] (MVP)** — As a hotel owner, I want to cancel a booking with a reason (e.g. overbooking, maintenance) so that the guest is refunded in full and informed.
    - Owner cancellations always give a full refund and are logged for admin review.
14. **O-BOOK-05 Reply to reviews [Added] (Phase 2)** — As a hotel owner, I want to reply publicly to guest reviews so that I can address feedback.

**Dashboard and reports**

15. **O-DASH-01 Dashboard (MVP)** — As a hotel owner, I want a dashboard of key figures for a chosen period so that I can see how my hotel performs.
    - Figures: revenue (gross, net after commission and refunds), number of bookings, room nights sold, occupancy rate, average daily rate (ADR), RevPAR, cancellation rate, average lead time.
    - Charts: revenue and bookings over time; bookings by room type; comparison with the previous period.
    - Filters by date range and room type; export reports to CSV/PDF.
16. **O-DASH-02 Payouts [Added] (Phase 2)** — As a hotel owner, I want to see what the platform owes me, the commission deducted and payout history so that I can reconcile my income.

## 9. Admin

The original requirements name the admin only as the approver of owner changes; the rest of this section is **[Added]** because the platform cannot run without it.

**Approvals**

1. **A-APPR-01 Approval queue (MVP)** — As an admin, I want a queue of pending hotel and room changes so that only checked data goes live.
   - Each request shows the hotel, owner, what changed (before vs after), and submission date.
   - Admin can approve, or reject with a mandatory reason that the owner sees.
   - Approved changes go live in search immediately; the live version stays unchanged while a change is pending.
   - Only one open change request per item at a time.
   - Every decision is logged with admin name and time.
2. **A-APPR-02 Approval notifications [Added] (MVP)** — As a hotel owner, I want an email when my change is approved or rejected so that I can act quickly.

**Users and hotels**

3. **A-USER-01 Manage customers [Added] (MVP)** — As an admin, I want to search customers, see their bookings and block or unblock accounts so that I can handle support and abuse.
4. **A-USER-02 Manage hotel owners [Added] (MVP) (updated)** — As an admin, I want to approve, invite and suspend partners (hotel owner businesses) and their users so that only verified partners sell rooms.
   - Each hotel belongs to exactly one partner, which receives the payouts; several users (owners, managers, staff) can manage a partner.
   - If a hotel changes owner, it is moved to the new partner; past bookings and payouts keep the old partner.
5. **A-HOTEL-01 Manage all hotels [Added] (MVP)** — As an admin, I want to view, edit, hide or deactivate any hotel so that I can remove problem listings.
6. **A-ROLE-01 Admin roles [Added] (Phase 2)** — As a super admin, I want to create admin users with permissions (e.g. content, finance, support) so that access is limited to what each person needs.

**Bookings and payments**

7. **A-BOOK-01 All bookings [Added] (MVP)** — As an admin, I want to search all bookings across hotels and cancel or refund one when needed so that I can resolve disputes.
8. **A-PAY-01 Payments and refunds [Added] (MVP)** — As an admin, I want to see Stripe payment and refund status per booking and retry failed refunds so that money issues are traceable.
9. **A-PAY-02 Commission and payouts [Added] (Phase 2) (updated)** — As an admin, I want to set the commission rate per partner and generate payout statements so that revenue is shared correctly.
   - The commission rate is copied onto each booking, so later rate changes don't affect past bookings.

**Content and configuration**

10. **A-CONT-01 Featured destinations [Added] (MVP) (updated) 🟡 Partly implemented** — As an admin, I want to choose which destinations and hotels appear on the homepage so that I can promote them.
    - Destinations (country, city, district, ward) are synced automatically from hotel addresses; places with no active hotel are hidden but kept.
    - The admin can rename a destination, add an image, a city-centre point and a featured order; the sync never overwrites these.
    - Left to do: choosing featured hotels, and setting a destination's city-centre point (the `center_lat`/`center_lng` columns exist, but no admin endpoint sets them).
11. **A-CONT-02 Master data [Added] (MVP) (updated)** — As an admin, I want to manage the lists of facilities, services, room features, bed types, board types and accommodation types so that filters stay consistent.
    - Destinations are not entered by hand; they come from the sync (A-CONT-01).
12. **A-DISC-01 Discount codes [Added] (MVP)** — As an admin, I want to create discount codes (percentage or fixed, validity dates, min spend, usage limits, eligible hotels, who pays the discount) so that I can run campaigns.
13. **A-CONF-01 Currencies [Added] (MVP)** — As an admin, I want to enable display currencies and see the last exchange-rate update so that converted prices are trustworthy.
14. **A-CONF-02 Taxes [Added] (MVP) (updated)** — As an admin, I want to configure tax rules per country so that checkout totals are correct.
    - Each rule has a name (e.g. VAT), a type (percentage of the room price, fixed per room-night, fixed per guest-night or fixed per booking), whether it is already included in the price, and validity dates.
    - A rate change is a new rule with new dates, so past bookings keep the rate they were charged.
    - Taxes are not set per city or per hotel; hotel service charges are part of the room price.
15. **A-REV-01 Moderate reviews [Added] (Phase 2)** — As an admin, I want to hide reviews that break the rules so that ratings stay fair.

**Platform dashboard**

16. **A-DASH-01 Platform dashboard [Added] (Phase 2)** — As an admin, I want platform-wide figures (GMV, commission earned, bookings, cancellations, top hotels and destinations, new users) so that I can track the business.
17. **A-AUDIT-01 Audit log [Added] (Phase 2)** — As an admin, I want a log of important actions (approvals, price changes, cancellations, refunds) so that I can investigate issues.

## 10. Gaps, non-functional requirements and open questions

**Biggest gaps found in the original requirements**

| Area | What was missing | Covered by |
| --- | --- | --- |
| Cancellation and refunds | Customer cancel flow, penalty calculation, Stripe refunds | C-MYBK-03, C-PAY-05, O-BOOK-04 |
| Inventory | Availability and price per date, room hold during payment, overbooking protection | O-ROOM-03, C-BOOK-04 |
| Rate plans | Same room sold with different board types and cancellation policies | O-ROOM-02, C-HOTEL-04 |
| Check-in / check-out | Physical room numbers, room assignment, check-in and check-out statuses | O-ROOM-01, O-BOOK-03 |
| Admin role | Everything except approval: users, hotels, discounts, taxes, currencies, master data | Section 9 |
| Notifications | Confirmation, cancellation, approval and reminder emails | C-PAY-03, O-BOOK-02, A-APPR-02, C-MYBK-07 |
| Account basics | Forgot password, profile, sign out, owner onboarding | C-AUTH-04 to 06, O-ACC-01 |
| Search usability | Sorting, modify search, no-results state | C-SRCH-03, 04, 07 |
| Reviews | "Rate from user" is shown but nothing creates it | C-MYBK-05, C-HOTEL-07 |
| Commission and payouts | How the platform earns and pays owners | A-PAY-02, O-DASH-02 |

**Non-functional requirements**

- **Performance:** search results return within 2 seconds for 95% of requests; pages are usable on mobile.
- **Concurrency:** two customers cannot book the last room at the same time (database lock and a database rule that rooms booked never exceed rooms available).
- **Security:** passwords and tokens hashed; short-lived access tokens with rotating refresh tokens; HTTPS everywhere; role-based access so owners only see their own hotels; Stripe webhooks verified by signature; rate limiting on sign-in and discount code checks.
- **Payments:** PCI scope kept minimal by using Stripe-hosted card fields; all amounts stored in minor units with currency code.
- **Time zones:** dates and cancellation deadlines use the hotel's local time zone.
- **Data and privacy:** consent for marketing emails; ability to export or delete personal data (including wishlist and recently viewed hotels).
- **Third-party limits:** Google Maps/Places and exchange-rate API calls are cached to control cost and quota.
- **Observability:** error tracking and logs for payment and booking flows; alerts on failed webhooks.
- **SEO:** hotel and destination pages are indexable with readable URLs.

**Decided**

- [x] Booking statuses: Pending, Confirmed, Checked-in, Checked-out, Cancelled, Expired, No-show.
- [x] Tax is calculated on the discounted price by default (configurable per tax rule).
- [x] Taxes are per country only; hotel service charges are included in room prices.
- [x] Daily prices and inventory go live without admin approval; hotel content and new room types need approval.
- [x] One partner per hotel; many users per partner.
- [x] Destinations are synced from hotel addresses; search history is not stored.
- [x] Sold-out hotels are hidden from search results, not shown as "Sold out".
- [x] Sessions: the refresh token rotates on every use and reusing an old one revokes the session; the last used time is stored, no device name. Every request checks the access token's session, so sign-out takes effect at once.
- [x] The cart holds rooms from any number of hotels and dates; each checkout books one hotel for one date range (one booking, one payment), checked by both the client and the API.
- [x] Booking needs an account: guests sign in (their browser cart merges into the account) before checking out (14 Nov 2026).
- [x] A booking is charged in the hotel's currency; the total in the customer's display currency comes with exchange rates (14 Nov 2026).

**Open questions to decide**

- [ ] Season/weekend pricing: keep reusable price rules, or only bulk-edit daily prices?
- [ ] Who funds discount codes: the platform, the hotel, or shared?
- [ ] Can a customer cancel one room from a multi-room booking, or only the whole booking?
- [ ] Is "user rating" from our own reviews only at launch, and what is shown before a hotel has any reviews?
- [ ] Does the platform collect full payment upfront (merchant model) or does the guest pay at the hotel for some rates?
