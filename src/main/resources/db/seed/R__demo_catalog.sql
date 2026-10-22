-- Demo hotel catalog for LOCAL DEVELOPMENT ONLY: application-local.yml adds db/seed to Flyway's locations;
-- the test and prod profiles never load it. Hotel names are fictional. Every insert is skipped when its row
-- already exists, so Flyway can re-run this file whenever it changes. To start over: docker compose down -v.

do $$
declare
    demo_partner bigint;
    h            record;
    new_address  bigint;
    new_hotel    bigint;
begin
    select id into demo_partner from partners where contact_email = 'demo-partner@example.com';
    if demo_partner is null then
        insert into partners (legal_name, contact_email, status, approved_at, created_at, updated_at)
        values ('Demo Hospitality Co.', 'demo-partner@example.com', 'APPROVED', now(), now(), now())
        returning id into demo_partner;
    end if;

    for h in
        select * from (values
            ('song-han-riverside-demo', 'Sông Hàn Riverside Hotel', 'hotel', 4, '35 Bạch Đằng', 'Thạch Thang', 'Hải Châu', 'Đà Nẵng', 'Đà Nẵng', 8.7, 412, 4.5, 'ACTIVE'),
            ('han-market-boutique-demo', 'Hàn Market Boutique', 'hotel', 3, '12 Trần Phú', 'Hải Châu 1', 'Hải Châu', 'Đà Nẵng', 'Đà Nẵng', 8.1, 158, 4.2, 'ACTIVE'),
            ('my-khe-beach-resort-demo', 'Mỹ Khê Beach Resort', 'resort', 5, '120 Võ Nguyên Giáp', 'Phước Mỹ', 'Sơn Trà', 'Đà Nẵng', 'Đà Nẵng', 9.2, 873, 4.7, 'ACTIVE'),
            ('son-tra-sea-view-demo', 'Sơn Trà Sea View Apartments', 'apartment', 4, '8 Hồ Nghinh', 'Phước Mỹ', 'Sơn Trà', 'Đà Nẵng', 'Đà Nẵng', 8.4, 96, 4.3, 'ACTIVE'),
            ('saigon-central-demo', 'Saigon Central Hotel', 'hotel', 4, '45 Lê Lợi', 'Bến Nghé', 'Quận 1', 'Hồ Chí Minh', 'Hồ Chí Minh', 8.6, 640, 4.4, 'ACTIVE'),
            ('ben-thanh-hostel-demo', 'Bến Thành Backpackers', 'hostel', 2, '9 Bùi Viện', 'Phạm Ngũ Lão', 'Quận 1', 'Hồ Chí Minh', 'Hồ Chí Minh', 7.9, 1204, 4.1, 'ACTIVE'),
            ('hoan-kiem-lake-demo', 'Hoàn Kiếm Lake Hotel', 'hotel', 4, '22 Hàng Bài', 'Hàng Bài', 'Hoàn Kiếm', 'Hà Nội', 'Hà Nội', 8.9, 530, 4.6, 'ACTIVE'),
            ('hoi-an-lantern-villa-demo', 'Hội An Lantern Villa', 'villa', 4, '5 Trần Hưng Đạo', 'Minh An', null, 'Hội An', 'Quảng Nam', 9.0, 310, 4.8, 'ACTIVE'),
            -- Not listed: its city must not appear in autocomplete.
            ('hue-imperial-demo', 'Huế Imperial Inn', 'hotel', 3, '2 Lê Lợi', 'Vĩnh Ninh', null, 'Huế', 'Thừa Thiên Huế', 8.0, 75, 4.0, 'INACTIVE')
        ) as v(slug, name, type_code, stars, line1, ward, district, city, province, rating, rating_count,
               google, status)
    loop
        continue when exists (select 1 from hotels where slug = h.slug);

        insert into addresses (line1, ward, district, city, state_province, country_code, created_at, updated_at)
        values (h.line1, h.ward, h.district, h.city, h.province, 'VN', now(), now())
        returning id into new_address;

        insert into hotels (partner_id, accommodation_type_id, name, slug, star_rating, address_id, user_rating_avg,
                            user_rating_count, google_rating, currency, timezone, status, created_at, updated_at)
        values (demo_partner, (select id from accommodation_types where code = h.type_code), h.name, h.slug, h.stars,
                new_address, h.rating, h.rating_count, h.google, 'VND', 'Asia/Ho_Chi_Minh', h.status, now(), now())
        returning id into new_hotel;

        insert into room_types (hotel_id, name, max_adults, max_children, max_occupancy, total_rooms, status,
                                sort_order, created_at, updated_at)
        values (new_hotel, 'Deluxe Double', 2, 0, 2, 5, 'ACTIVE', 1, now(), now()),
               (new_hotel, 'Family Suite', 2, 2, 4, 2, 'ACTIVE', 2, now(), now());

        insert into hotel_photos (hotel_id, url, category, is_cover, sort_order, created_at, updated_at)
        values (new_hotel, 'https://picsum.photos/seed/' || h.slug || '/800/600', 'exterior', true, 0, now(), now());
    end loop;
end $$;

-- Availability demo, relative to the day this seed first ran: the Family Suite of Sông Hàn Riverside is
-- closed on day 7 and sold out on day 8, so families searching those nights do not see that hotel.
insert into room_inventory (room_type_id, stay_date, total_rooms, booked_rooms, is_closed)
select rt.id, current_date + n.day, 2, n.booked, n.closed
from room_types rt
join hotels h on h.id = rt.hotel_id
cross join (values (7, 0, true), (8, 2, false)) as n(day, booked, closed)
where h.slug = 'song-han-riverside-demo' and rt.name = 'Family Suite'
on conflict (room_type_id, stay_date) do nothing;

-- Featured homepage cities. The spellings match the addresses above, so their generated keys are the places
-- the destination sync finds, and it only fills in their hotel counts.
insert into destinations (type, name, slug, country_code, state_province, city, image_url, featured_rank,
                          created_at, updated_at)
values ('CITY', 'Đà Nẵng', 'vn-da-nang', 'VN', 'Đà Nẵng', 'Đà Nẵng',
        'https://picsum.photos/seed/vn-da-nang/1200/800', 1, now(), now()),
       ('CITY', 'Hồ Chí Minh', 'vn-ho-chi-minh', 'VN', 'Hồ Chí Minh', 'Hồ Chí Minh',
        'https://picsum.photos/seed/vn-ho-chi-minh/1200/800', 2, now(), now()),
       ('CITY', 'Hà Nội', 'vn-ha-noi', 'VN', 'Hà Nội', 'Hà Nội',
        'https://picsum.photos/seed/vn-ha-noi/1200/800', 3, now(), now()),
       ('CITY', 'Hội An', 'vn-hoi-an', 'VN', 'Quảng Nam', 'Hội An',
        'https://picsum.photos/seed/vn-hoi-an/1200/800', 4, now(), now())
on conflict (country_code, province_key, city_key, district_key, ward_key) where deleted_at is null do nothing;
