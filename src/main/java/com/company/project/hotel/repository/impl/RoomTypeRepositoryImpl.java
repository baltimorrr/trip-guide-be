package com.company.project.hotel.repository.impl;

import java.util.Collection;
import java.util.List;

import com.company.project.hotel.repository.RoomBedView;
import com.company.project.hotel.repository.RoomFeatureView;
import com.company.project.hotel.repository.RoomPhotoView;
import com.company.project.hotel.repository.RoomTypeDetailView;
import com.company.project.hotel.repository.RoomTypeRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Room type queries for the hotel page, all JPQL. */
@Repository
@RequiredArgsConstructor
public class RoomTypeRepositoryImpl implements RoomTypeRepository {

	private static final String DETAILS = """
			select new com.company.project.hotel.repository.RoomTypeDetailView(
			       rt.id, rt.name, rt.description, rt.sizeSqm, rt.maxAdults, rt.maxChildren, rt.maxOccupancy)
			from RoomType rt
			where rt.id in :ids""";

	private static final String BEDS = """
			select new com.company.project.hotel.repository.RoomBedView(rt.id, bt.code, bt.name, b.quantity)
			from RoomType rt
			join rt.beds b
			join b.bedType bt
			where rt.id in :ids
			order by rt.id, bt.id""";

	private static final String FEATURES = """
			select new com.company.project.hotel.repository.RoomFeatureView(rt.id, am.code, am.name, am.icon)
			from RoomType rt
			join rt.features am
			where rt.id in :ids and am.scope = com.company.project.hotel.model.AmenityScope.ROOM
			order by rt.id, am.groupName, am.name""";

	private static final String PHOTOS = """
			select new com.company.project.hotel.repository.RoomPhotoView(p.roomTypeId, p.url)
			from HotelPhoto p
			where p.roomTypeId in :ids
			order by p.roomTypeId, p.sortOrder, p.id""";

	private final EntityManager entityManager;

	@Override
	public List<RoomTypeDetailView> findDetails(Collection<Long> roomTypeIds) {
		return entityManager.createQuery(DETAILS, RoomTypeDetailView.class).setParameter("ids", roomTypeIds).getResultList();
	}

	@Override
	public List<RoomBedView> findBeds(Collection<Long> roomTypeIds) {
		return entityManager.createQuery(BEDS, RoomBedView.class).setParameter("ids", roomTypeIds).getResultList();
	}

	@Override
	public List<RoomFeatureView> findFeatures(Collection<Long> roomTypeIds) {
		return entityManager.createQuery(FEATURES, RoomFeatureView.class).setParameter("ids", roomTypeIds).getResultList();
	}

	@Override
	public List<RoomPhotoView> findPhotos(Collection<Long> roomTypeIds) {
		return entityManager.createQuery(PHOTOS, RoomPhotoView.class).setParameter("ids", roomTypeIds).getResultList();
	}

}
