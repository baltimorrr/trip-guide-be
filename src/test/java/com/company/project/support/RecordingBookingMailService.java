package com.company.project.support;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import com.company.project.booking.dto.BookingResponse;
import com.company.project.booking.services.BookingMailService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Keeps every booking email per booking reference, so a test reads what the customer and the hotel were sent. Every
 * {@link IntegrationTest} gets it through {@link Config}; tests use their own bookings, so they never read each
 * other's emails.
 */
public class RecordingBookingMailService implements BookingMailService {

	/** One email: its kind, its recipient and the booking it carried. */
	public record Mail(String kind, String to, BookingResponse booking) {
	}

	private static final Map<String, List<Mail>> MAILS = new ConcurrentHashMap<>();

	private static final AtomicBoolean FAIL_NEXT = new AtomicBoolean();

	@Override
	public void sendConfirmation(String email, BookingResponse booking) {
		record("confirmation", email, booking);
	}

	@Override
	public void notifyOwnerOfNewBooking(String email, BookingResponse booking) {
		record("new-booking", email, booking);
	}

	@Override
	public void sendCancellation(String email, BookingResponse booking) {
		record("cancellation", email, booking);
	}

	@Override
	public void notifyOwnerOfCancellation(String email, BookingResponse booking) {
		record("owner-cancellation", email, booking);
	}

	/** Every email sent about this booking, oldest first. */
	public static List<Mail> mails(String reference) {
		return List.copyOf(MAILS.getOrDefault(reference, List.of()));
	}

	/** The emails of this kind sent about this booking. */
	public static List<Mail> mails(String reference, String kind) {
		return mails(reference).stream().filter(mail -> mail.kind().equals(kind)).toList();
	}

	/** The next email fails as if the mail server were down. */
	public static void failNext() {
		FAIL_NEXT.set(true);
	}

	private static void record(String kind, String email, BookingResponse booking) {
		if (FAIL_NEXT.getAndSet(false)) {
			throw new IllegalStateException("Fake mail outage");
		}
		MAILS.computeIfAbsent(booking.reference(), key -> new CopyOnWriteArrayList<>()).add(new Mail(kind, email, booking));
	}

	/** Replaces the logging booking mailer in the shared test context. */
	@TestConfiguration(proxyBeanMethods = false)
	public static class Config {

		@Bean
		@Primary
		RecordingBookingMailService recordingBookingMailService() {
			return new RecordingBookingMailService();
		}

	}

}
