package com.company.project.common.persistence;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.query.sqm.function.SqmFunctionRegistry;
import org.hibernate.type.BasicTypeRegistry;
import org.hibernate.type.StandardBasicTypes;

/**
 * Makes the database's own SQL functions callable from JPQL with a known return type, so queries can
 * write {@code norm(h.name)} and Hibernate renders exactly {@code norm(...)}, the expression the indexes use.
 * Registered through {@code META-INF/services/org.hibernate.boot.model.FunctionContributor}.
 */
public class DatabaseFunctionContributor implements FunctionContributor {

	@Override
	public void contributeFunctions(FunctionContributions functions) {
		BasicTypeRegistry types = functions.getTypeConfiguration().getBasicTypeRegistry();
		SqmFunctionRegistry registry = functions.getFunctionRegistry();
		// norm(text): accent-, case- and spacing-insensitive form of a name (migration V3).
		registry.registerPattern("norm", "norm(?1)", types.resolve(StandardBasicTypes.STRING));
		// pg_trgm. word_similar(a, b) is the indexable "a <% b": some stretch of b's words is similar to a, above
		// pg_trgm.word_similarity_threshold (0.6 by default). word_similarity(a, b) is that similarity, 0 to 1.
		registry.registerPattern("word_similar", "(?1 <% ?2)", types.resolve(StandardBasicTypes.BOOLEAN));
		registry.registerPattern("word_similarity", "word_similarity(?1, ?2)",
				types.resolve(StandardBasicTypes.DOUBLE));
	}

}
