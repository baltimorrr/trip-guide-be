---
phase: 4
title: Booking page and confirmation emails
status: completed
priority: P2
dependencies:
  - 3
---

# Phase 4: Booking page and confirmation emails

## Overview

C-PAY-03 gives the customer proof of the reservation. It adds a `GET /api/bookings/{reference}` for the confirmation
page, and a confirmation email to the booker plus a new-booking notice to the hotel's partner contact. Both emails
are written to the log after the confirming transaction commits.

## Requirements

- Functional:
  - `GET /api/bookings/{reference}` → `BookingResponse`. It's the owner only; anyone else, or an unknown reference,
    gets 404. The response carries:
    - booking: reference, status, hold end (Pending only), `confirmedAt`
    - **hotel**: name, slug, full address (line1, line2, ward, district, city, state, postal code, country), phone,
      email, check-in from, check-out until
    - stay: dates, nights; the booker and the guest name; special requests and arrival time
    - rooms: name, rate plan, board, guests, lead guest, cancellation terms computed now from the stored policy and
      night prices
    - the price breakdown
    - `payment` `{status, failureReason, paidAt}`, or null
    - `cancellation` (null until phase 5)
  - `POST /api/bookings` returns the same shape. It's additive: `hotel` gains fields and `confirmedAt`/`payment` are
    null.
  - On `BookingConfirmed`, after commit:
    - `sendConfirmation(bookerEmail, booking)`
    - `notifyOwnerOfNewBooking(partner contact_email, booking)`
  - The mail payload is the `BookingResponse` above, so the email holds everything C-PAY-03 lists: reference, hotel
    name and address, dates, rooms, guests, board, cancellation policy, price breakdown and hotel contact.
- Non-functional:
  - Mail never runs inside a transaction, and a mail failure never fails the webhook: it's caught and logged at
    ERROR.
  - The log impl writes recipient + reference at INFO and the rendered body at DEBUG. The body holds personal data,
    so it stays out of INFO logs.

## Architecture

```text
BookingController  GET /{reference}  -> BookingService.find(userId, reference)  @Transactional(readOnly = true)
BookingMapper.toBooking(Booking, BookingHotel, Map<Long,String> ratePlanNames, Payment, Instant now)   // one mapping for POST and GET
HotelRepositoryCustom.findBookingHotel(hotelId)        JPQL select new BookingHotel(...) from Hotel join address
HotelRepositoryCustom.findPartnerContactEmail(hotelId)  native read (partners has no entity), like findCommissionRate

booking/notification/BookingMailListener
  @TransactionalEventListener(phase = AFTER_COMMIT) on(BookingConfirmed e)
    booking = bookingService.mailView(e.bookingId())   // REQUIRES_NEW read-only: the committed tx can't be reused
    mails.sendConfirmation(booking.booker().email(), booking)
    mails.notifyOwnerOfNewBooking(ownerEmail, booking)  // skipped with WARN when the partner has no contact email
booking/services/BookingMailService  -> impl/BookingMailServiceImpl (logs, like AuthMailServiceImpl)
```

Room cancellation terms in the GET reuse the hotel page's `RateCancellation` builder (`HotelMapper`: `CancellationSchedule.tiers` +
`FreeCancellation.until`), fed the stored `CancellationTerms`, the room's stored night prices and the hotel's check-in
time and zone. Move that builder to a shared static helper if `HotelMapper` keeps it private; don't copy it.

## Related Code Files

- Modify: `booking/controller/BookingController.java` (GET), `booking/services/BookingService.java` + impl
  (`find`, `mailView`), `booking/mapper/BookingMapper.java` (entity-based `toBooking`; `create` uses it too)
- Modify: `booking/dto/BookingResponse.java` (`confirmedAt`, `payment`, `cancellation`; `hotel` becomes
  `BookingHotel`), create `booking/dto/BookingHotel.java`, `booking/dto/BookingCancellation.java` (fields filled in
  phase 5)
- Modify: `hotel/model/Hotel.java` (map `phone`, `email`), `hotel/repository/HotelRepositoryCustom` + impl
  (`findBookingHotel`, `findPartnerContactEmail`), `hotel/mapper/HotelMapper.java` (extract the `RateCancellation`
  builder if needed)
- Create: `booking/notification/BookingMailListener.java`, `booking/services/BookingMailService.java`,
  `booking/services/impl/BookingMailServiceImpl.java`
- Reuse: `booking/dto/PaymentDetails` (from phase 2) as `BookingResponse.payment`; `booking/` stays free of
  `payment/` imports.
- Create (test): `support/RecordingBookingMailService.java` (+ `Config`, `@Primary`; keeps the mails per reference),
  import it in `support/IntegrationTest.java`; `booking/controller/BookingControllerReadTest.java`,
  `booking/notification/BookingMailListenerTest.java`

## Implementation Steps

1. Map `Hotel.phone`/`email`. Add `findBookingHotel` (JPQL `select new`, the address joined) and
   `findPartnerContactEmail` (native `select p.contact_email from hotels h join partners p ...`, with a comment why
   native).
2. Entity-based `toBooking`:
   - Rate plan names come from one JPQL read by the rooms' `ratePlanId`s.
   - Taxes come from `BookingTax`, totals from the booking columns.
   - Switch `BookingServiceImpl.create` to it, and remove the old `PricedGroup` overload once
     `BookingControllerTest` passes unchanged. If it doesn't, the difference is a bug in one of the two: fix it,
     don't keep both.
3. `find(userId, reference)`: `findByReferenceAndUserId`, else `ResourceNotFoundException` (404), plus the
   booking's latest payment (`PaymentRepository.findFirstByBookingIdOrderByIdDesc`).
4. Mail service: the interface plus a logging impl, with one method per message (`sendConfirmation`,
   `notifyOwnerOfNewBooking`, and phase 5's `sendCancellation`, `notifyOwnerOfCancellation`).
5. Listener as above, catching `RuntimeException` around each send.
6. Test recorder: like `RecordingAuthMailService`, static `confirmation(reference)` / `ownerNotice(reference)`
   accessors returning `(to, BookingResponse)`.

## Success Criteria

- [ ] After a confirming webhook (phase 3 helper), `GET /api/bookings/{reference}` shows:
  - Confirmed, `confirmedAt`, payment SUCCEEDED with `paidAt`
  - the hotel's address, phone and email
  - each room's board, guests, lead guest and cancellation terms
  - the same price breakdown as the POST response
- [ ] Another user's reference → 404. A Pending booking → payment null before start and REQUIRES_PAYMENT after.
- [ ] `POST /api/bookings` tests pass unchanged, apart from the added fields.
- [ ] On confirmation, the recorder holds:
  - one confirmation to the booker email
  - one owner notice to `partner-{suffix}@test.local` (from `CatalogFixtures`)
  - both carry the reference, hotel name and address, dates, rooms, guests, board, cancellation terms, price and
    hotel contact
- [ ] The same webhook delivered twice → still one confirmation and one owner notice.
- [ ] The zero-total start (phase 2) also sends both mails.
- [ ] A recorder set to throw → the webhook still answers 200, the booking stays Confirmed, and an ERROR is logged.

## Risk Assessment

- `AFTER_COMMIT` listeners run in the committing thread, with the transaction's resources still bound. Reading
  without `REQUIRES_NEW` silently joins a finished transaction. The listener test catches lazy-loading errors.
- An email lost between commit and send (crash): accepted while emails are only logged. Noted in README
  limitations; `email_outbox` is the real fix later.
- Response shape change on POST is additive only. The frontend plan builds no booking pages yet.
