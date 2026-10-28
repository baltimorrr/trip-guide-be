package com.company.project.wishlist.repository.impl;

import java.time.Instant;
import java.util.List;

import com.company.project.hotel.repository.HotelJpql;
import com.company.project.wishlist.repository.WishlistEntryView;
import com.company.project.wishlist.repository.WishlistRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

/**
 * The wishlist's statements, all JPQL. Adding is an HQL insert with {@code on conflict do nothing} and removing a
 * bulk delete, so neither loads the entry and neither fails when another request got there first.
 */
@Repository
@RequiredArgsConstructor
public class WishlistRepositoryImpl implements WishlistRepository {

	private static final String ADD = """
			insert into WishlistEntry (userId, hotelId, createdAt) values (:userId, :hotelId, :savedAt)
			on conflict do nothing""";

	private static final String REMOVE = "delete from WishlistEntry w where w.userId = :userId and w.hotelId = :hotelId";

	private static final String PAGE = """
			select new com.company.project.wishlist.repository.WishlistEntryView(w.hotelId, w.createdAt,
			       case when %s then true else false end)
			from WishlistEntry w
			join Hotel h on h.id = w.hotelId
			where w.userId = :userId
			order by w.createdAt desc, w.hotelId desc""".formatted(HotelJpql.HOTEL_LISTED);

	private static final String COUNT = "select count(w) from WishlistEntry w where w.userId = :userId";

	private final EntityManager entityManager;

	@Override
	public int addIfAbsent(long userId, long hotelId, Instant savedAt) {
		return entityManager.createQuery(ADD)
			.setParameter("userId", userId)
			.setParameter("hotelId", hotelId)
			.setParameter("savedAt", savedAt)
			.executeUpdate();
	}

	@Override
	public int remove(long userId, long hotelId) {
		return entityManager.createQuery(REMOVE)
			.setParameter("userId", userId)
			.setParameter("hotelId", hotelId)
			.executeUpdate();
	}

	@Override
	public Page<WishlistEntryView> findPage(long userId, Pageable pageable) {
		List<WishlistEntryView> content = entityManager.createQuery(PAGE, WishlistEntryView.class)
			.setParameter("userId", userId)
			.setFirstResult(Math.toIntExact(pageable.getOffset()))
			.setMaxResults(pageable.getPageSize())
			.getResultList();
		return PageableExecutionUtils.getPage(content, pageable, () -> entityManager
			.createQuery(COUNT, Long.class)
			.setParameter("userId", userId)
			.getSingleResult());
	}

}
