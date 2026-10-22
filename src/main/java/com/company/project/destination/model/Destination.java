package com.company.project.destination.model;

import java.time.Instant;

import com.company.project.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * A place travellers search and browse. Rows are inserted and counted by the destination sync (SQL);
 * this entity only edits what admins own: display name, image and featured rank.
 * <p>
 * Columns the sync owns are {@code updatable = false}, so an admin save can never write back a stale
 * hotel count or spelling. The database generates {@code normalized_name} from {@code name} and the keys
 * (a place's identity) from the spellings.
 */
@Entity
@Table(name = "destinations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Destination extends BaseEntity {

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20, updatable = false)
	private DestinationType type;

	@Column(nullable = false, length = 150)
	private String name;

	@Column(name = "normalized_name", insertable = false, updatable = false)
	private String normalizedName;

	@Column(nullable = false, length = 200, updatable = false)
	private String slug;

	@Column(name = "country_code", nullable = false, length = 2, updatable = false)
	private String countryCode;

	@Column(name = "state_province", length = 100, updatable = false)
	private String stateProvince;

	@Column(length = 100, updatable = false)
	private String city;

	@Column(length = 100, updatable = false)
	private String district;

	@Column(length = 100, updatable = false)
	private String ward;

	@Column(name = "province_key", insertable = false, updatable = false)
	private String provinceKey;

	@Column(name = "city_key", insertable = false, updatable = false)
	private String cityKey;

	@Column(name = "district_key", insertable = false, updatable = false)
	private String districtKey;

	@Column(name = "ward_key", insertable = false, updatable = false)
	private String wardKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10, updatable = false)
	private DestinationSource source;

	@Column(name = "hotel_count", nullable = false, updatable = false)
	private int hotelCount;

	@Column(name = "last_synced_at", updatable = false)
	private Instant lastSyncedAt;

	@Column(name = "image_url", length = 500)
	private String imageUrl;

	@Column(name = "featured_rank")
	private Short featuredRank;

	@Column(name = "deleted_at", updatable = false)
	private Instant deletedAt;

	/** Admin-owned fields. A null rank removes the destination from the homepage. */
	public void updateDisplay(String name, String imageUrl, Short featuredRank) {
		rename(name);
		this.imageUrl = StringUtils.hasText(imageUrl) ? imageUrl.trim() : null;
		this.featuredRank = featuredRank;
	}

	/** New display name; the database regenerates the normalized name from it. */
	public void rename(String name) {
		this.name = name.trim();
	}

}
