package com.company.project.common.config;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The Swagger UI examples an annotation can't hold: stay dates, which must lie in the future. Every parameter and
 * schema property named {@code checkIn} or {@code checkOut} gets a {@value #NIGHTS}-night stay starting
 * {@value #DAYS_AHEAD} days after the API docs are built, so the search, the hotel page's rooms and the cart run
 * against the demo seed with nothing typed. Every other example is a {@code @Schema} or {@code @Parameter} on its DTO
 * or controller.
 */
@Configuration
class OpenApiExamples {

	static final int DAYS_AHEAD = 30;

	static final int NIGHTS = 2;

	@Bean
	OpenApiCustomizer stayDateExamples() {
		return openApi -> {
			LocalDate checkIn = LocalDate.now(ZoneOffset.UTC).plusDays(DAYS_AHEAD);
			Map<String, String> dates = Map.of("checkIn", checkIn.toString(),
					"checkOut", checkIn.plusDays(NIGHTS).toString());
			if (openApi.getPaths() != null) {
				openApi.getPaths().values().stream()
						.flatMap(path -> path.readOperations().stream())
						.map(operation -> operation.getParameters())
						.filter(Objects::nonNull)
						.flatMap(parameters -> parameters.stream())
						.filter(parameter -> dates.containsKey(parameter.getName()))
						.forEach(parameter -> parameter.setExample(dates.get(parameter.getName())));
			}
			if (openApi.getComponents() != null && openApi.getComponents().getSchemas() != null) {
				for (Schema<?> schema : openApi.getComponents().getSchemas().values()) {
					if (schema.getProperties() != null) {
						dates.forEach((name, date) -> {
							if (schema.getProperties().containsKey(name)) {
								schema.getProperties().get(name).setExample(date);
							}
						});
					}
				}
			}
		};
	}

}
