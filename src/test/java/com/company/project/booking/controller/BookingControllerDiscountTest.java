package com.company.project.booking.controller;

import static com.company.project.booking.controller.BookingClient.money;
import static com.company.project.support.CatalogFixtures.DEFAULT_PRICE;
import static com.company.project.support.CatalogFixtures.uniqueSuffix;
import static com.company.project.support.TestAuth.body;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.company.project.support.CatalogFixtures;
import com.company.project.support.CatalogFixtures.CreatedHotel;
import com.company.project.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Discount codes at checkout (C-BOOK-03): each rule refuses a code with its own message, and a valid code comes off
 * the room subtotal before the tax. Codes share one table, so every code here carries the test's suffix.
 */
@IntegrationTest
class BookingControllerDiscountTest {

	static final LocalDate CHECK_IN = LocalDate.now().plusDays(30);

	static final LocalDate CHECK_OUT = CHECK_IN.plusDays(2);

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	CatalogFixtures catalog;

	String suffix;

	BookingClient client;

	CreatedHotel hotel;

	/** Two rooms for two nights at the default price: 4,000,000 VND. */
	long line;

	@BeforeEach
	void setUp() {
		catalog = new CatalogFixtures(jdbc);
		suffix = uniqueSuffix();
		client = BookingClient.signUp(mvc, catalog);
		hotel = catalog.hotel("Discounted " + suffix).create();
		line = client.addToCart(catalog.defaultRatePlan(catalog.roomType(hotel.id(), 2, 2, 4, 5)), CHECK_IN, CHECK_OUT,
				2, 2);
	}

	@Test
	void unknownInactiveAndDeletedCodesAreNotValid() {
		catalog.discountCode("off-" + suffix).inactive().create();
		catalog.discountCode("gone-" + suffix).deleted().create();

		assertRefused("nothing-" + suffix, "This discount code is not valid");
		assertRefused("off-" + suffix, "This discount code is not valid");
		assertRefused("gone-" + suffix, "This discount code is not valid");
	}

	@Test
	void aCodeIsUsableOnlyBetweenItsDates() {
		Instant now = Instant.now();
		catalog.discountCode("soon-" + suffix).live(now.plus(1, ChronoUnit.DAYS), now.plus(9, ChronoUnit.DAYS)).create();
		catalog.discountCode("over-" + suffix).live(now.minus(9, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS)).create();

		assertRefused("soon-" + suffix, "This discount code is not active yet");
		assertRefused("over-" + suffix, "This discount code has expired");
	}

	@Test
	void aCodeForOtherHotelsOrAnotherCurrencyIsRefused() {
		long other = catalog.hotel("Not discounted " + suffix).create().id();
		catalog.discountCode("there-" + suffix).hotels(other).create();
		catalog.discountCode("here-" + suffix).hotels(other, hotel.id()).create();
		catalog.discountCode("dollars-" + suffix).fixed("10", "USD").create();

		assertRefused("there-" + suffix, "This discount code can't be used at this hotel");
		assertThat(client.quote("here-" + suffix, line)).hasStatusOk();
		assertRefused("dollars-" + suffix, "This discount code can't be used for prices in VND");
	}

	@Test
	void aMinimumSpendMustBeReached() {
		catalog.discountCode("big-" + suffix).minSpend(5_000_000, "VND").create();
		catalog.discountCode("enough-" + suffix).minSpend(4 * DEFAULT_PRICE, "VND").create();

		assertRefused("big-" + suffix, "This discount code needs rooms worth at least 5000000 VND");
		assertThat(client.quote("enough-" + suffix, line)).hasStatusOk();
	}

	@Test
	void theUsageLimitsCountEveryUse() {
		long usedUp = catalog.discountCode("once-" + suffix).limits(1, null).create();
		long usedByMe = catalog.discountCode("mine-" + suffix).limits(10, 1).create();
		redeem(usedUp, catalog.user());
		redeem(usedByMe, client.userId);

		assertRefused("once-" + suffix, "This discount code has been fully used");
		assertRefused("mine-" + suffix, "You have already used this discount code");
		BookingClient someoneElse = BookingClient.signUp(mvc, catalog);
		long theirLine = someoneElse.addToCart(catalog.defaultRatePlan(catalog.roomType(hotel.id(), 2, 2, 4, 5)),
				CHECK_IN, CHECK_OUT, 1, 2);
		assertThat(someoneElse.quote("mine-" + suffix, theirLine)).hasStatusOk();
	}

	@Test
	void aFirstBookingCodeRefusesSomeoneWhoHasBooked() {
		catalog.discountCode("first-" + suffix).firstBookingOnly().create();
		assertThat(client.quote("first-" + suffix, line)).hasStatusOk();

		catalog.booking(client.userId, catalog.hotel("Stayed " + suffix).create().id());

		assertRefused("first-" + suffix, "This discount code is only for a first booking");
	}

	@Test
	void aPercentIsCappedAndTheTaxIsChargedOnTheDiscountedPrice() {
		CreatedHotel taxed = catalog.hotel("Taxed discount " + suffix).country("XF").create();
		catalog.taxRule("XF", "VAT", "PERCENT", "10", null, false, true);
		long taxedLine = client.addToCart(catalog.defaultRatePlan(catalog.roomType(taxed.id(), 2, 2, 4, 5)), CHECK_IN,
				CHECK_OUT, 2, 2);
		catalog.discountCode("Capped-" + suffix).percent("15", 500_000L, "VND").create();

		MvcTestResult quote = client.quote(" CAPPED-" + suffix.toUpperCase() + " ", taxedLine);

		assertThat(quote).hasStatusOk();
		assertThat(JsonPath.<String>read(body(quote), "$.price.discount.code")).isEqualTo("capped-" + suffix);
		assertThat(money(JsonPath.read(body(quote), "$.price.subtotal"))).isEqualTo(4_000_000);
		assertThat(money(JsonPath.read(body(quote), "$.price.discount.amount"))).isEqualTo(500_000);
		assertThat(money(JsonPath.read(body(quote), "$.price.taxes[0].amount"))).isEqualTo(350_000);
		assertThat(money(JsonPath.read(body(quote), "$.price.total"))).isEqualTo(3_850_000);
	}

	@Test
	void aFixedAmountComesOffButNeverBelowZero() {
		catalog.discountCode("fixed-" + suffix).fixed("300000", "VND").create();
		catalog.discountCode("huge-" + suffix).fixed("9000000", "VND").create();

		MvcTestResult fixed = client.quote("fixed-" + suffix, line);
		MvcTestResult huge = client.quote("huge-" + suffix, line);

		assertThat(money(JsonPath.read(body(fixed), "$.price.discount.amount"))).isEqualTo(300_000);
		assertThat(money(JsonPath.read(body(fixed), "$.price.total"))).isEqualTo(3_700_000);
		assertThat(money(JsonPath.read(body(huge), "$.price.discount.amount"))).isEqualTo(4_000_000);
		assertThat(money(JsonPath.read(body(huge), "$.price.total"))).isZero();
	}

	private void assertRefused(String code, String message) {
		MvcTestResult quote = client.quote(code, line);
		assertThat(quote).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(JsonPath.<String>read(body(quote), "$.detail")).isEqualTo(message);
	}

	/** A use of the code by {@code userId}, through a past booking of theirs. */
	private void redeem(long codeId, long userId) {
		jdbc.sql("""
				insert into discount_redemptions (discount_code_id, booking_id, user_id, amount_minor, created_at,
				                                  updated_at)
				values (:codeId, :bookingId, :userId, 1000, now(), now())""")
			.param("codeId", codeId)
			.param("bookingId", catalog.booking(userId, hotel.id()))
			.param("userId", userId)
			.update();
	}

}
