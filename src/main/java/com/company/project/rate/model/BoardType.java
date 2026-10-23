package com.company.project.rate.model;

import com.company.project.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Reference data seeded by migration V5: RO room only, BB breakfast, HB half board, FB full board, AI all inclusive. */
@Entity
@Table(name = "board_types")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BoardType extends BaseEntity {

	@Column(nullable = false, length = 10)
	private String code;

	@Column(nullable = false, length = 60)
	private String name;

}
