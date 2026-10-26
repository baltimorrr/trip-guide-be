package com.company.project.search.config;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Hotel search limits under {@code app.search}.
 *
 * @param maxNights longest stay a search accepts
 * @param maxDaysAhead how far in the future check-in may be
 * @param maxRooms most rooms in one search
 * @param maxAdults most adults in one search
 * @param maxChildren most children in one search
 * @param ratingSortMinReviews reviews a hotel needs to be ranked by its user rating when results are sorted by
 * rating, so a single 10/10 review does not top the list
 */
@Validated
@ConfigurationProperties("app.search")
public record SearchProperties(@Positive int maxNights, @Positive int maxDaysAhead, @Positive int maxRooms,
		@Positive int maxAdults, @PositiveOrZero int maxChildren, @Positive int ratingSortMinReviews) {
}
