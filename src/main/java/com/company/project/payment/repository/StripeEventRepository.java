package com.company.project.payment.repository;

import com.company.project.payment.model.StripeEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/** Stripe webhook events received. Hand-written queries are in {@link StripeEventRepositoryCustom}. */
public interface StripeEventRepository extends JpaRepository<StripeEvent, String>, StripeEventRepositoryCustom {

	/** Whether this event was already handled; a repeat is answered 200 and changes nothing. */
	boolean existsByIdAndProcessedAtIsNotNull(String id);

}
