package com.company.project.discount.services.impl;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

import com.company.project.common.dto.Money;
import com.company.project.common.exception.BusinessException;
import com.company.project.discount.model.AppliedDiscount;
import com.company.project.discount.model.DiscountCode;
import com.company.project.discount.model.DiscountRedemption;
import com.company.project.discount.model.DiscountUse;
import com.company.project.discount.repository.DiscountCodeRepository;
import com.company.project.discount.repository.DiscountRedemptionRepository;
import com.company.project.discount.services.DiscountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiscountServiceImpl implements DiscountService {

	private final DiscountCodeRepository codes;

	private final DiscountRedemptionRepository redemptions;

	@Override
	public AppliedDiscount check(DiscountUse use) {
		return apply(codes.findLiveByCode(DiscountCode.normalize(use.code())), use);
	}

	@Override
	@Transactional
	public AppliedDiscount reserve(DiscountUse use) {
		return apply(codes.findLiveByCodeForUpdate(DiscountCode.normalize(use.code())), use);
	}

	@Override
	@Transactional
	public void redeem(AppliedDiscount discount, long bookingId, long userId) {
		redemptions.save(DiscountRedemption.create(discount.discountCodeId(), bookingId, userId,
				discount.amountMinor()));
	}

	@Override
	@Transactional
	public void releaseUses(Collection<Long> bookingIds) {
		redemptions.deleteByBookingIdIn(bookingIds);
	}

	private AppliedDiscount apply(Optional<DiscountCode> found, DiscountUse use) {
		DiscountCode code = found.orElseThrow(() -> refused("This discount code is not valid"));
		Instant now = Instant.now();
		if (!code.hasStarted(now)) {
			throw refused("This discount code is not active yet");
		}
		if (code.hasEnded(now)) {
			throw refused("This discount code has expired");
		}
		if (!code.appliesTo(use.hotelId())) {
			throw refused("This discount code can't be used at this hotel");
		}
		if (!code.sellsIn(use.currency())) {
			throw refused("This discount code can't be used for prices in " + use.currency());
		}
		if (!code.isMetBy(use.subtotalMinor())) {
			throw refused("This discount code needs rooms worth at least %s %s".formatted(
					Money.ofMinor(code.getMinSpendMinor(), use.currency()).amount().toPlainString(), use.currency()));
		}
		if (code.getUsageLimitTotal() != null
				&& redemptions.countByDiscountCodeId(code.getId()) >= code.getUsageLimitTotal()) {
			throw refused("This discount code has been fully used");
		}
		if (code.getUsageLimitPerUser() != null && redemptions.countByDiscountCodeIdAndUserId(code.getId(),
				use.userId()) >= code.getUsageLimitPerUser()) {
			throw refused("You have already used this discount code");
		}
		if (code.isFirstBookingOnly() && !use.firstBooking()) {
			throw refused("This discount code is only for a first booking");
		}
		long amount = code.amountOff(use.subtotalMinor(), use.currency());
		if (amount == 0) {
			throw refused("This discount code takes nothing off these rooms");
		}
		return new AppliedDiscount(code.getId(), code.getCode(), amount);
	}

	private static BusinessException refused(String message) {
		return new BusinessException(HttpStatus.BAD_REQUEST, message);
	}

}
