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

-- Prices, relative to the day this part first ran. Two platform cancellation policies; every demo room type sold
-- room-only with free cancellation, or with breakfast at 10% less but non-refundable; +20% on Friday and Saturday
-- nights for the room-only plans; one date override; and Vietnam's VAT added on top of the price.
do $$
declare
    free_policy   bigint;
    strict_policy bigint;
begin
    select id into free_policy from cancellation_policies
    where hotel_id is null and name = 'Free cancellation until 24 hours before check-in';
    if free_policy is null then
        insert into cancellation_policies (name, is_refundable, created_at, updated_at)
        values ('Free cancellation until 24 hours before check-in', true, now(), now())
        returning id into free_policy;
        insert into cancellation_policy_rules (policy_id, hours_before_check_in, penalty_type, penalty_value,
                                               created_at, updated_at)
        values (free_policy, 24, 'PERCENT', 100, now(), now());
    end if;

    select id into strict_policy from cancellation_policies where hotel_id is null and name = 'Non-refundable';
    if strict_policy is null then
        insert into cancellation_policies (name, is_refundable, created_at, updated_at)
        values ('Non-refundable', false, now(), now())
        returning id into strict_policy;
        insert into cancellation_policy_rules (policy_id, hours_before_check_in, penalty_type, penalty_value,
                                               created_at, updated_at)
        values (strict_policy, 100000, 'PERCENT', 100, now(), now());
    end if;

    insert into rate_plans (room_type_id, name, board_type_id, cancellation_policy_id, base_price_minor, currency,
                            created_at, updated_at)
    select rt.id, p.name, (select id from board_types where code = p.board),
           case when p.refundable then free_policy else strict_policy end,
           cast(case rt.name when 'Family Suite' then 500000 else 300000 end as bigint) * h.star_rating * p.factor / 100,
           h.currency, now(), now()
    from room_types rt
    join hotels h on h.id = rt.hotel_id
    cross join (values ('Room only, free cancellation', 'RO', true, 100),
                       ('Breakfast, non-refundable', 'BB', false, 90)) as p(name, board, refundable, factor)
    where h.slug like '%-demo' and not exists (select 1 from rate_plans rp where rp.room_type_id = rt.id);

    insert into rate_rules (rate_plan_id, name, date_from, date_to, days_of_week, adjustment, percent, created_at,
                            updated_at)
    select rp.id, 'Weekend', current_date, current_date + 365, '{5,6}', 'PERCENT', 20, now(), now()
    from rate_plans rp
    join room_types rt on rt.id = rp.room_type_id
    join hotels h on h.id = rt.hotel_id
    join board_types b on b.id = rp.board_type_id
    where h.slug like '%-demo' and b.code = 'RO'
      and not exists (select 1 from rate_rules r where r.rate_plan_id = rp.id);

    -- New Year's Eve style override: the date price wins over the weekend rule and the base price.
    insert into rate_plan_prices (rate_plan_id, stay_date, price_minor)
    select rp.id, current_date + 10, 9999000
    from rate_plans rp
    join room_types rt on rt.id = rp.room_type_id
    join hotels h on h.id = rt.hotel_id
    join board_types b on b.id = rp.board_type_id
    where h.slug = 'my-khe-beach-resort-demo' and rt.name = 'Deluxe Double' and b.code = 'RO'
      and not exists (select 1 from rate_plan_prices pp where pp.rate_plan_id = rp.id);

    insert into tax_rules (country_code, name, calc_type, value, is_included, created_at, updated_at)
    select 'VN', 'VAT', 'PERCENT', 10, false, now(), now()
    where not exists (select 1 from tax_rules where country_code = 'VN' and name = 'VAT');
end $$;

-- Filter demo (C-SRCH-02): distance to the city centre, hotel facilities, and the beds and features of each demo
-- room type. Deluxe Double has a king bed in 4-5 star hotels and a double bed elsewhere; Family Suite has twin and
-- queen beds, a balcony, and a sea view by the beach.
update hotels h
set distance_to_center_m = v.metres
from (values ('song-han-riverside-demo', 600), ('han-market-boutique-demo', 300),
             ('my-khe-beach-resort-demo', 4200), ('son-tra-sea-view-demo', 3500),
             ('saigon-central-demo', 400), ('ben-thanh-hostel-demo', 900),
             ('hoan-kiem-lake-demo', 250), ('hoi-an-lantern-villa-demo', 1200),
             ('hue-imperial-demo', 800)) as v(slug, metres)
where h.slug = v.slug and h.distance_to_center_m is distinct from v.metres;

insert into hotel_amenities (hotel_id, amenity_id)
select h.id, a.id
from (values ('song-han-riverside-demo', '{wifi,parking,front_desk_24h,restaurant,bar,fitness}'),
             ('han-market-boutique-demo', '{wifi,front_desk_24h}'),
             ('my-khe-beach-resort-demo', '{wifi,parking,airport_shuttle,restaurant,bar,pool,spa,fitness,beach_access}'),
             ('son-tra-sea-view-demo', '{wifi,parking,pool,beach_access,pets_allowed}'),
             ('saigon-central-demo', '{wifi,front_desk_24h,restaurant,bar,pool,fitness,airport_shuttle}'),
             ('ben-thanh-hostel-demo', '{wifi,bar,front_desk_24h}'),
             ('hoan-kiem-lake-demo', '{wifi,front_desk_24h,restaurant,spa}'),
             ('hoi-an-lantern-villa-demo', '{wifi,parking,pool,pets_allowed}'),
             ('hue-imperial-demo', '{wifi,parking}')) as v(slug, codes)
join hotels h on h.slug = v.slug
join amenities a on a.code = any (cast(v.codes as text[]))
on conflict (hotel_id, amenity_id) do nothing;

insert into room_type_beds (room_type_id, bed_type_id, quantity)
select rt.id, b.id, v.quantity
from room_types rt
join hotels h on h.id = rt.hotel_id
join (values ('Deluxe Double', true, 'king', 1), ('Deluxe Double', false, 'double', 1),
             ('Family Suite', true, 'twin', 1), ('Family Suite', true, 'queen', 1),
             ('Family Suite', false, 'twin', 1), ('Family Suite', false, 'queen', 1))
    as v(room_type, upscale, bed, quantity)
    on v.room_type = rt.name and v.upscale = (h.star_rating >= 4)
join bed_types b on b.code = v.bed
where h.slug like '%-demo'
on conflict (room_type_id, bed_type_id) do nothing;

insert into room_type_amenities (room_type_id, amenity_id)
select rt.id, a.id
from room_types rt
join hotels h on h.id = rt.hotel_id
join amenities a on a.code = any (case rt.name
        when 'Family Suite' then case when h.slug in ('my-khe-beach-resort-demo', 'son-tra-sea-view-demo')
                                      then '{air_conditioning,balcony,sea_view,bathtub,kitchen}'::text[]
                                      else '{air_conditioning,balcony,city_view,bathtub}'::text[] end
        else '{air_conditioning,minibar,work_desk}'::text[] end)
where h.slug like '%-demo'
on conflict (room_type_id, amenity_id) do nothing;
