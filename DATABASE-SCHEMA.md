// Hotel Booking Platform — database schema for dbdiagram.io
// Generated from schema.sql (PostgreSQL 16). Paste into https://dbdiagram.io
// Edited by hand to match the code: destinations (stored spellings), users.first_failed_login_at,
// user_sessions.previous_token_hash and last_used_at; carts (any hotel, dates per item in cart_items; signed-in
// users only, so no guest_token); cart_items (one row per rate, stay and guests).
// Conventions: money = bigint *_minor (cents) + char(3) currency; a night = stay_date in [check_in, check_out)

Project hotel_booking {
database_type: 'PostgreSQL'
Note: '''
Hotel booking platform: client site (search, detail, cart, booking, Stripe payment),
hotel owner and admin site (hotel data, approvals, bookings, dashboard).
Partial indexes, CHECK constraints and triggers are listed in table notes;
schema.sql is the source of truth.
'''
}

// ------------------------------------------------------------------ Enums

Enum amenity_scope {
hotel
room
}

Enum app_role {
customer
hotel_owner
hotel_staff
admin
}

Enum booking_room_status {
active
cancelled
}

Enum booking_status {
pending
confirmed
checked_in
checked_out
cancelled
expired
no_show
}

Enum change_action {
create
update
delete
}

Enum change_entity {
hotel
room_type
rate_plan
hotel_photo
hotel_policy
hotel_amenity
}

Enum change_status {
draft
pending
approved
rejected
cancelled
}

Enum destination_source {
sync
admin
}

Enum destination_type {
country
city
district
ward
}

Enum discount_funder {
platform
hotel
shared
}

Enum discount_type {
percent
fixed
}

Enum listing_status {
draft
pending_review
active
inactive
suspended
}

Enum member_role {
owner
staff
}

Enum outbox_status {
queued
sent
failed
}

Enum partner_status {
pending
approved
suspended
}

Enum payment_status {
requires_payment
processing
succeeded
failed
canceled
}

Enum payout_status {
draft
approved
paid
}

Enum penalty_type {
percent
nights
fixed
}

Enum price_adjustment {
set_price
percent
amount
}

Enum refund_status {
pending
succeeded
failed
}

Enum review_status {
published
hidden
}

Enum room_status {
available
out_of_service
}

Enum tax_calc_type {
percent
per_night
per_booking
per_person_night
}

Enum token_type {
email_verify
password_reset
}

Enum user_status {
active
blocked
deleted
}

// ------------------------------------------------------------------ users_and_partners

Table users {
id uuid [pk, default: `gen_random_uuid()`]
email citext [not null, unique]
password_hash text [note: 'null for Google-only accounts']
full_name text [not null]
phone text
country_code char(2)
preferred_currency char(3)
preferred_language text [not null, default: 'en']
email_verified_at timestamptz
status user_status [not null, default: 'active']
failed_login_count smallint [not null, default: 0]
first_failed_login_at timestamptz [note: 'start of the current lockout window']
locked_until timestamptz
deleted_at timestamptz
created_at timestamptz [not null, default: `now()`]
updated_at timestamptz [not null, default: `now()`]
}

Table user_roles {
user_id uuid [not null]
role app_role [not null]
created_at timestamptz [not null, default: `now()`]

indexes {
(user_id, role) [pk]
}
}

Table auth_identities {
id uuid [pk, default: `gen_random_uuid()`]
user_id uuid [not null]
provider text [not null, note: '\'google\'']
provider_user_id text [not null, note: 'Google "sub"']
email citext
created_at timestamptz [not null, default: `now()`]

indexes {
(provider, provider_user_id) [unique]
}
}

Table user_tokens {
id uuid [pk, default: `gen_random_uuid()`]
user_id uuid [not null]
type token_type [not null]
token_hash text [not null, unique]
expires_at timestamptz [not null]
used_at timestamptz
created_at timestamptz [not null, default: `now()`]
}

Table user_sessions {
id uuid [pk, default: `gen_random_uuid()`]
user_id uuid [not null]
refresh_token_hash text [not null, unique]
previous_token_hash text [note: 'hash replaced by the last rotation; presenting it again revokes the session']
user_agent text
ip inet [note: 'varchar(45) in the code']
expires_at timestamptz [not null, note: 'fixed at sign-in; rotation does not extend it']
last_used_at timestamptz
revoked_at timestamptz
created_at timestamptz [not null, default: `now()`]

indexes {
user_id [name: 'user_sessions_user_id_idx']
previous_token_hash [name: 'user_sessions_previous_token_hash_idx']
}
}

Table partners {
id uuid [pk, default: `gen_random_uuid()`]
legal_name text [not null]
tax_id text
contact_email citext [not null]
phone text
commission_rate numeric(5,2) [not null, default: 15.00]
status partner_status [not null, default: 'pending']
approved_by uuid
approved_at timestamptz
deleted_at timestamptz [note: 'soft delete']
created_at timestamptz [not null, default: `now()`]
updated_at timestamptz [not null, default: `now()`]

Note: '''
CHECK (((commission_rate >= (0)::numeric) AND (commission_rate <= (100)::numeric)))
'''
}

Table partner_members {
partner_id uuid [not null]
user_id uuid [not null]
member_role member_role [not null, default: 'owner']
created_at timestamptz [not null, default: `now()`]

indexes {
(partner_id, user_id) [pk]
user_id [name: 'partner_members_user_id_idx']
}
}

Table partner_documents {
id uuid [pk, default: `gen_random_uuid()`]
partner_id uuid [not null]
doc_type text [not null, note: 'business_licence, tax_certificate…']
file_url text [not null]
uploaded_by uuid
uploaded_at timestamptz [not null, default: `now()`]
}

// ------------------------------------------------------------------ hotel_catalog

Table destinations {
id uuid [pk, default: `gen_random_uuid()`]
type destination_type [not null]
name text [not null, note: 'display name, e.g. "Đà Nẵng" (admin may edit)']
normalized_name text [not null, note: 'generated: norm(name)']
slug text [not null, unique, note: 'vn-da-nang']
country_code char(2) [not null]
state_province text [note: 'most common spelling of addresses.state_province among the hotels here']
city text [note: 'most common spelling of addresses.city; null below the level of the place']
district text [note: 'most common spelling of addresses.district; null below the level of the place']
ward text [note: 'most common spelling of addresses.ward; null below the level of the place']
province_key text [not null, note: 'generated: norm(state_province), empty string when null']
city_key text [not null, note: 'generated: norm(city), empty string when null']
district_key text [not null, note: 'generated: norm(district), empty string when null']
ward_key text [not null, note: 'generated: norm(ward), empty string when null']
source destination_source [not null, default: 'sync']
hotel_count integer [not null, default: 0, note: 'active hotels here, refreshed by the sync']
last_synced_at timestamptz
google_place_id text [unique, note: 'Google place of the city, when known']
center_lat numeric(9,6) [note: 'city centre, for "distance from centre"']
center_lng numeric(9,6)
timezone text [note: 'e.g. Asia/Ho_Chi_Minh']
image_url text
featured_rank smallint [note: 'admins pick which destinations appear in the homepage "popular destinations" section, and in what order: not null = shown, lowest first']
deleted_at timestamptz [note: 'soft delete']
created_at timestamptz [not null, default: `now()`]
updated_at timestamptz [not null, default: `now()`]

indexes {
normalized_name [name: 'destination_autocomplete', note: 'gin (normalized_name gin_trgm_ops), partial: WHERE ((deleted_at IS NULL) AND (hotel_count > 0))']
(country_code, province_key, city_key, district_key, ward_key) [name: 'destination_place_key', note: 'UNIQUE only WHERE (deleted_at IS NULL)']
featured_rank [name: 'destinations_featured_rank_idx', note: 'partial: WHERE (featured_rank IS NOT NULL)']
}

Note: '''
Places travellers search and browse: country, city, district, ward.
No parent column: the level a place belongs to is read from its keys
(a ward with city_key 'da nang' is inside the city row with the same keys).
A lookup table SYNCED FROM addresses (no foreign key between them):
sync_destinations() copies every country / city / district / ward used by an
active hotel's address into this table, refreshes the stored spellings (the most
common one among the place's hotels, reused by the places inside it) and
refreshes hotel_count.
The *_key columns are generated from the spellings, so every spelling that
normalises alike is one place, and a stored spelling always normalises to its key.
Admins then add what addresses cannot give: images, featured order, centre point,
a nicer display name. The sync never overwrites those columns.
Search finds hotels by matching the *_key columns against the same normalised
fields of addresses (norm(city), norm(district)…), see queries.sql Q1.
Autocomplete matches the name only (pg_trgm word similarity, text <% normalized_name),
so a half-typed "hai ch" and the typo "hai chua" both find Hải Châu. Suggestions
join the country row for the country shown in their label.
CHECK ((country_code ~ '^[A-Z]{2}$'::text))
CHECK (((normalized_name <> ''::text) AND (normalized_name = lower(normalized_name))))
CHECK ((hotel_count >= 0))
CHECK (((type <> 'country'::destination_type) OR ((city_key = ''::text) AND (district_key = ''::text) AND (ward_key = ''::text) AND (province_key = ''::text))))
CHECK (((type <> 'city'::destination_type) OR ((city_key <> ''::text) AND (district_key = ''::text) AND (ward_key = ''::text))))
CHECK (((type <> 'district'::destination_type) OR ((city_key <> ''::text) AND (district_key <> ''::text) AND (ward_key = ''::text))))
CHECK (((type <> 'ward'::destination_type) OR ((city_key <> ''::text) AND (ward_key <> ''::text))))
'''
}

Table addresses {
id uuid [pk, default: `gen_random_uuid()`]
line1 text [not null, note: 'house number + street: "35 Vo Nguyen Giap"']
line2 text [note: 'building, floor, unit']
ward text [note: 'ward / commune (phuong / xa)']
district text [note: 'district (quan / huyen); nullable: not every country has one']
city text [not null, note: 'city / town']
state_province text [note: 'province / state (tinh)']
postal_code text
country_code char(2) [not null, note: 'ISO 3166-1 alpha-2, e.g. VN']
latitude numeric(9,6)
longitude numeric(9,6)
google_place_id text [note: 'place the address was verified against']
formatted text [note: 'one-line display, e.g. from Google']
verified_at timestamptz [note: 'when checked against Google Places']
created_at timestamptz [not null, default: `now()`]
updated_at timestamptz [not null, default: `now()`]

indexes {
(latitude, longitude) [name: 'addresses_latitude_longitude_idx']
}

Note: '''
Structured address, shared by any table that needs one (hotels now; partners or
billing later). Fields follow Google Places address components so data entered by
owners and data returned by Google line up. Display text is built from the parts.
CHECK (((latitude IS NULL) = (longitude IS NULL)))
CHECK ((country_code ~ '^[A-Z]{2}$'::text))
CHECK ((((latitude >= ('-90'::integer)::numeric) AND (latitude <= (90)::numeric)) AND ((longitude >= ('-180'::integer)::numeric) AND (longitude <= (180)::numeric))))
Index address_place_match on (country_code, COALESCE(norm(state_province), ''::text), COALESCE(norm(city), ''::text), COALESCE(norm(district), ''::text), COALESCE(norm(ward), ''::text))  (uses function norm() from schema.sql)
'''
}

Table hotels {
id uuid [pk, default: `gen_random_uuid()`]
partner_id uuid [not null]
accommodation_type_id smallint [not null]
name text [not null]
slug text [not null, unique]
description text
star_rating smallint
address_id uuid [not null, unique, note: '1 hotel : 1 address']
distance_to_center_m integer [note: 'computed when the address location changes']
google_place_id text [unique, note: 'the hotel as a Google place (for ratings)']
google_rating numeric(2,1)
google_rating_count integer
google_synced_at timestamptz
user_rating_avg numeric(3,1)
user_rating_count integer [not null, default: 0]
currency char(3) [not null, note: 'selling currency']
timezone text [not null]
check_in_from time [not null, default: '14:00:00']
check_in_until time
check_out_until time [not null, default: '12:00:00']
min_check_in_age smallint [default: 18]
phone text
email citext
status listing_status [not null, default: 'draft']
deleted_at timestamptz
created_at timestamptz [not null, default: `now()`]
updated_at timestamptz [not null, default: `now()`]

indexes {
`to_tsvector('simple'::regconfig, name)` [name: 'hotels_name_search', note: 'gin index']
partner_id [name: 'hotels_partner_id_idx']
}

Note: '''
CHECK (((star_rating >= 1) AND (star_rating <= 5)))
CHECK (((google_rating >= (0)::numeric) AND (google_rating <= (5)::numeric)))
CHECK (((user_rating_avg >= (0)::numeric) AND (user_rating_avg <= (10)::numeric)))
'''
}

Table hotel_photos {
id uuid [pk, default: `gen_random_uuid()`]
hotel_id uuid [not null]
room_type_id uuid [note: 'null = hotel photo']
url text [not null]
category text [note: 'room, pool, lobby, exterior…']
caption text
sort_order smallint [not null, default: 0]
is_cover boolean [not null, default: false]
created_at timestamptz [not null, default: `now()`]

indexes {
(hotel_id, sort_order) [name: 'hotel_photos_hotel_id_sort_order_idx']
hotel_id [name: 'one_cover_per_hotel', note: 'UNIQUE only WHERE (is_cover AND (room_type_id IS NULL))']
}
}

Table hotel_policies {
id uuid [pk, default: `gen_random_uuid()`]
hotel_id uuid [not null]
policy_type text [not null, note: 'children, pets, smoking, deposit, extra_bed, payment, other']
description text [not null]
sort_order smallint [not null, default: 0]
created_at timestamptz [not null, default: `now()`]
updated_at timestamptz [not null, default: `now()`]

indexes {
hotel_id [name: 'hotel_policies_hotel_id_idx']
}
}

Table amenities {
id integer [pk, increment]
code text [not null, unique, note: 'wifi, pool, parking, aircon…']
name text [not null]
group_name text [not null, note: 'General, Wellness, Bathroom…']
scope amenity_scope [not null]
icon text
}

Table hotel_amenities {
hotel_id uuid [not null]
amenity_id integer [not null]
is_free boolean [not null, default: true]

indexes {
(hotel_id, amenity_id) [pk]
amenity_id [name: 'hotel_amenities_amenity_id_idx']
}
}

Table accommodation_types {
id smallint [pk, increment]
code text [not null, unique, note: 'hotel, resort, apartment…']
name text [not null]
}

// ------------------------------------------------------------------ rooms_rates_availability

Table room_types {
id uuid [pk, default: `gen_random_uuid()`]
hotel_id uuid [not null]
name text [not null]
description text
size_sqm numeric(6,1)
max_adults smallint [not null]
max_children smallint [not null, default: 0]
max_occupancy smallint [not null]
total_rooms smallint [not null, note: 'default rooms per night']
status listing_status [not null, default: 'draft']
sort_order smallint [not null, default: 0]
deleted_at timestamptz [note: 'soft delete (bookings keep pointing here)']
created_at timestamptz [not null, default: `now()`]
updated_at timestamptz [not null, default: `now()`]

indexes {
(id, hotel_id) [unique]
hotel_id [name: 'room_types_hotel_id_idx', note: 'partial: WHERE (deleted_at IS NULL)']
}

Note: '''
CHECK ((max_adults >= 1))
CHECK ((total_rooms >= 0))
CHECK ((max_occupancy >= max_adults))
'''
}

Table rooms {
id uuid [pk, default: `gen_random_uuid()`]
hotel_id uuid [not null]
room_type_id uuid [not null]
room_number text [not null, note: '"101", "A-12"']
floor smallint
status room_status [not null, default: 'available']
notes text
deleted_at timestamptz [note: 'soft delete']
created_at timestamptz [not null, default: `now()`]
updated_at timestamptz [not null, default: `now()`]

indexes {
(id, room_type_id) [unique]
(hotel_id, room_number) [name: 'room_number_per_hotel', note: 'UNIQUE only WHERE (deleted_at IS NULL)']
room_type_id [name: 'rooms_room_type_id_idx', note: 'partial: WHERE (deleted_at IS NULL)']
}

Note: '''
Physical rooms ("101", "A-12"). Room types say how many rooms are sold per night;
rooms are what the front desk hands out at check-in (room_assignments).
The composite FK guarantees a room's type belongs to the same hotel.
'''
}

Table room_type_beds {
room_type_id uuid [not null]
bed_type_id smallint [not null]
quantity smallint [not null, default: 1]

indexes {
(room_type_id, bed_type_id) [pk]
}

Note: '''
CHECK ((quantity >= 1))
'''
}

Table bed_types {
id smallint [pk, increment]
code text [not null, unique, note: 'single, double, queen, king, twin']
name text [not null]
}

Table room_type_amenities {
room_type_id uuid [not null]
amenity_id integer [not null]

indexes {
(room_type_id, amenity_id) [pk]
}
}

Table rate_plans {
id uuid [pk, default: `gen_random_uuid()`]
room_type_id uuid [not null]
name text [not null, note: '"Breakfast included, free cancellation"']
board_type_id smallint [not null]
cancellation_policy_id uuid [not null]
base_price_minor bigint [not null, note: 'per room per night']
currency char(3) [not null]
min_nights smallint [not null, default: 1]
is_active boolean [not null, default: true]
deleted_at timestamptz [note: 'soft delete (bookings keep pointing here)']
created_at timestamptz [not null, default: `now()`]
updated_at timestamptz [not null, default: `now()`]

indexes {
room_type_id [name: 'rate_plans_room_type_id_idx', note: 'partial: WHERE (is_active AND (deleted_at IS NULL))']
}

Note: '''
CHECK ((base_price_minor >= 0))
'''
}

Table board_types {
id smallint [pk, increment]
code text [not null, unique, note: 'RO, BB, HB, FB, AI']
name text [not null]
}

Table cancellation_policies {
id uuid [pk, default: `gen_random_uuid()`]
hotel_id uuid [note: 'null = platform template']
name text [not null, note: '"Free until 48h before"']
is_refundable boolean [not null]
created_at timestamptz [not null, default: `now()`]
updated_at timestamptz [not null, default: `now()`]
}

Table cancellation_policy_rules {
id uuid [pk, default: `gen_random_uuid()`]
policy_id uuid [not null]
hours_before_check_in integer [not null]
penalty_type penalty_type [not null]
penalty_value numeric(12,2) [not null]

indexes {
(policy_id, hours_before_check_in) [unique]
}

Note: '''
"Cancelled less than hours_before_check_in hours before check-in costs penalty".
The applicable rule is the one with the SMALLEST hours_before_check_in that is
still greater than the hours remaining. Non-refundable = one rule with a huge N.
CHECK ((hours_before_check_in >= 0))
CHECK ((penalty_value >= (0)::numeric))
'''
}

Table room_inventory {
room_type_id uuid [not null]
stay_date date [not null]
total_rooms smallint [not null]
booked_rooms smallint [not null, default: 0, note: 'pending + confirmed']
is_closed boolean [not null, default: false, note: 'stop-sell']
updated_at timestamptz [not null, default: `now()`]

indexes {
(room_type_id, stay_date) [pk]
stay_date [name: 'room_inventory_stay_date_idx']
}

Note: '''
Rooms per room type per night. Shared by all rate plans of that room type.
CHECK ((total_rooms >= 0))
CHECK ((booked_rooms >= 0))
CHECK ((booked_rooms <= total_rooms))
'''
}

Table rate_plan_prices {
rate_plan_id uuid [not null]
stay_date date [not null]
price_minor bigint [not null]
min_nights smallint
is_closed boolean [not null, default: false]
updated_at timestamptz [not null, default: `now()`]

indexes {
(rate_plan_id, stay_date) [pk]
}

Note: '''
Price per rate plan per night (falls back to rate_plans.base_price_minor).
CHECK ((price_minor >= 0))
'''
}

Table rate_rules {
id uuid [pk, default: `gen_random_uuid()`]
rate_plan_id uuid [not null]
name text [not null, note: '"Summer high season", "Weekend", "Tet"']
date_from date [not null]
date_to date [not null, note: 'inclusive']
days_of_week "smallint[]" [note: 'ISO 1=Mon … 7=Sun; null = every day']
adjustment price_adjustment [not null]
amount_minor bigint [note: 'set_price: the price; amount: +/- change']
percent numeric(6,2) [note: 'percent: +20.00 or -15.00']
priority smallint [not null, default: 0, note: 'higher wins when rules overlap']
is_active boolean [not null, default: true]
created_at timestamptz [not null, default: `now()`]
updated_at timestamptz [not null, default: `now()`]

indexes {
(rate_plan_id, date_from, date_to) [name: 'rate_rules_rate_plan_id_date_from_date_to_idx', note: 'partial: WHERE is_active']
}

Note: '''
Season, weekend and holiday price rules. Price of one night, first match wins:
1. rate_plan_prices row for that date (owner's manual override)
2. active rate_rule with the highest priority covering the date and weekday
3. rate_plans.base_price_minor
   night_price() below implements exactly this order.
   CHECK ((date_to >= date_from))
   CHECK (((days_of_week IS NULL) OR ((cardinality(days_of_week) > 0) AND (days_of_week <@ ARRAY[(1)::smallint, (2)::smallint, (3)::smallint, (4)::smallint, (5)::smallint, (6)::smallint, (7)::smallint]))))
   CHECK ((((adjustment = 'set_price'::price_adjustment) AND (amount_minor >= 0) AND (percent IS NULL)) OR ((adjustment = 'amount'::price_adjustment) AND (amount_minor IS NOT NULL) AND (percent IS NULL)) OR ((adjustment = 'percent'::price_adjustment) AND (percent > ('-100'::integer)::numeric) AND (amount_minor IS NULL))))
   '''
   }

// ------------------------------------------------------------------ cart_and_booking

Table carts {
id uuid [pk, default: `gen_random_uuid()`]
user_id uuid [not null, unique]
created_at timestamptz [not null, default: `now()`]
updated_at timestamptz [not null, default: `now()`]

Note: '''
One cart per signed-in user. Guests keep their cart in the browser and merge it in after signing in.
'''
}

Table cart_items {
id uuid [pk, default: `gen_random_uuid()`]
cart_id uuid [not null]
rate_plan_id uuid [not null]
check_in date [not null]
check_out date [not null]
quantity smallint [not null, default: 1]
adults smallint [not null, note: 'per room']
children smallint [not null, default: 0]
child_ages "smallint[]" [not null, default: '{}']
price_at_add_minor bigint [not null, note: 'only for the "price changed" notice']
currency char(3) [not null]
created_at timestamptz [not null, default: `now()`]
updated_at timestamptz [not null, default: `now()`]

indexes {
(cart_id, rate_plan_id, check_in, check_out, adults, child_ages) [name: 'cart_items_line_key', unique, note: 'child_ages stored sorted']
check_in [name: 'cart_items_check_in_idx', note: 'expiry of passed check-ins']
}

Note: '''
A cart holds items of any hotel and dates; the hotel comes from the rate plan.
Items of one hotel with the same check_in and check_out are checked out together as one booking.
CHECK ((check_out > check_in))
CHECK ((quantity >= 1))
CHECK ((adults >= 1))
CHECK ((children >= 0))
CHECK ((cardinality(child_ages) = children))
CHECK (((0 <= ALL (child_ages)) AND (17 >= ALL (child_ages))))
CHECK ((price_at_add_minor >= 0))
'''
}

Table bookings {
id uuid [pk, default: `gen_random_uuid()`]
reference text [not null, unique, note: 'e.g. HB7K2M4Q']
user_id uuid [not null]
hotel_id uuid [not null]
check_in date [not null]
check_out date [not null]
nights smallint [note: 'generated: (check_out - check_in)']
status booking_status [not null, default: 'pending']
booker_name text [not null]
booker_email citext [not null]
booker_phone text [not null]
booker_country char(2)
guest_name text [note: 'when booking for someone else']
special_requests text
estimated_arrival time
terms_accepted_at timestamptz [not null]
currency char(3) [not null]
subtotal_minor bigint [not null]
discount_minor bigint [not null, default: 0]
tax_minor bigint [not null, default: 0, note: 'taxes added on top']
fee_minor bigint [not null, default: 0]
total_minor bigint [not null]
display_currency char(3)
display_total_minor bigint
fx_rate numeric(20,10)
discount_code_id uuid
commission_rate numeric(5,2) [not null]
commission_minor bigint [not null, default: 0]
hold_expires_at timestamptz
confirmed_at timestamptz
checked_in_at timestamptz
checked_out_at timestamptz
cancelled_at timestamptz
cancelled_by uuid
cancel_reason text
cancellation_fee_minor bigint
created_at timestamptz [not null, default: `now()`]
updated_at timestamptz [not null, default: `now()`]

indexes {
(hotel_id, check_in) [name: 'bookings_hotel_id_check_in_idx']
(hotel_id, status, created_at) [name: 'bookings_hotel_id_status_created_at_idx']
(user_id, check_in) [name: 'bookings_user_id_check_in_idx', note: 'DESC on check_in']
hold_expires_at [name: 'pending_holds', note: 'partial: WHERE (status = \'pending\'::booking_status)']
}

Note: '''
Trigger bookings_status_check: BEFORE INSERT OR UPDATE OF status -> booking_status_check()
Trigger bookings_status_log: AFTER INSERT OR UPDATE OF status -> booking_status_log()
CHECK ((check_out > check_in))
CHECK ((total_minor = (((subtotal_minor - discount_minor) + tax_minor) + fee_minor)))
CHECK (((status <> ALL (ARRAY['confirmed'::booking_status, 'checked_in'::booking_status, 'checked_out'::booking_status])) OR (confirmed_at IS NOT NULL)))
CHECK (((status <> ALL (ARRAY['checked_in'::booking_status, 'checked_out'::booking_status])) OR (checked_in_at IS NOT NULL)))
CHECK (((status <> 'checked_out'::booking_status) OR (checked_out_at IS NOT NULL)))
CHECK (((status <> 'cancelled'::booking_status) OR (cancelled_at IS NOT NULL)))
CHECK (((status <> 'pending'::booking_status) OR (hold_expires_at IS NOT NULL)))
'''
}

Table booking_rooms {
id uuid [pk, default: `gen_random_uuid()`]
booking_id uuid [not null]
room_type_id uuid [not null]
rate_plan_id uuid [not null]
room_name text [not null]
board_type_code text [not null]
cancellation_policy jsonb [not null, note: '{name, is_refundable, rules:[…]}']
adults smallint [not null]
children smallint [not null, default: 0]
child_ages "smallint[]" [not null, default: '{}']
lead_guest_name text
total_minor bigint [not null]
status booking_room_status [not null, default: 'active']
cancelled_at timestamptz
created_at timestamptz [not null, default: `now()`]

indexes {
(id, room_type_id) [unique]
booking_id [name: 'booking_rooms_booking_id_idx']
room_type_id [name: 'booking_rooms_room_type_id_idx']
}

Note: '''
One row per physical room booked (3 rooms = 3 rows).
'''
}

Table room_assignments {
id uuid [pk, default: `gen_random_uuid()`]
booking_room_id uuid [not null, unique]
room_type_id uuid [not null]
room_id uuid [not null]
stay daterange [not null, note: 'filled from the booking by a trigger']
assigned_by uuid
created_at timestamptz [not null, default: `now()`]

indexes {
room_id [name: 'room_assignments_room_id_idx']
}

Note: '''
Physical room given to a booked room, usually at check-in.
no_double_assignment makes it impossible to give one room to two overlapping stays;
ranges are [check_in, check_out), so a check-out and a check-in on the same day are fine.
Both composite FKs force the room and the booked room to be the same room type.
Trigger room_assignments_fill_stay: BEFORE INSERT OR UPDATE OF booking_room_id -> room_assignment_fill_stay()
CHECK ((NOT isempty(stay)))
EXCLUDE USING gist (room_id WITH =, stay WITH &&)  (exclusion constraint no_double_assignment)
'''
}

Table booking_room_nights {
booking_room_id uuid [not null]
stay_date date [not null]
price_minor bigint [not null]

indexes {
(booking_room_id, stay_date) [pk]
stay_date [name: 'booking_room_nights_stay_date_idx']
}
}

Table booking_taxes {
id uuid [pk, default: `gen_random_uuid()`]
booking_id uuid [not null]
tax_rule_id uuid
name text [not null]
is_included boolean [not null]
amount_minor bigint [not null]

indexes {
booking_id [name: 'booking_taxes_booking_id_idx']
}
}

Table booking_status_history {
id uuid [pk, default: `gen_random_uuid()`]
booking_id uuid [not null]
from_status booking_status
to_status booking_status [not null]
changed_by uuid [note: 'null = system']
reason text
created_at timestamptz [not null, default: `now()`]

indexes {
booking_id [name: 'booking_status_history_booking_id_idx']
}
}

// ------------------------------------------------------------------ payments_discounts_taxes

Table payments {
id uuid [pk, default: `gen_random_uuid()`]
booking_id uuid [not null]
provider text [not null, default: 'stripe']
stripe_payment_intent_id text [unique]
stripe_checkout_session_id text [unique]
amount_minor bigint [not null]
currency char(3) [not null]
status payment_status [not null, default: 'requires_payment']
failure_reason text
paid_at timestamptz
created_at timestamptz [not null, default: `now()`]
updated_at timestamptz [not null, default: `now()`]

indexes {
booking_id [name: 'one_successful_payment', note: 'UNIQUE only WHERE (status = \'succeeded\'::payment_status)']
booking_id [name: 'payments_booking_id_idx']
}

Note: '''
CHECK ((amount_minor >= 0))
'''
}

Table refunds {
id uuid [pk, default: `gen_random_uuid()`]
payment_id uuid [not null]
booking_id uuid [not null]
stripe_refund_id text [unique]
amount_minor bigint [not null]
currency char(3) [not null]
reason text
status refund_status [not null, default: 'pending']
initiated_by uuid
created_at timestamptz [not null, default: `now()`]
updated_at timestamptz [not null, default: `now()`]

indexes {
booking_id [name: 'refunds_booking_id_idx']
}

Note: '''
CHECK ((amount_minor > 0))
'''
}

Table stripe_events {
id text [pk, note: 'Stripe event id (evt_…): idempotency key']
type text [not null]
payload jsonb [not null]
received_at timestamptz [not null, default: `now()`]
processed_at timestamptz
error text
}

Table discount_codes {
id uuid [pk, default: `gen_random_uuid()`]
code citext [not null, unique]
description text
discount_type discount_type [not null]
value numeric(12,2) [not null, note: 'percent or major units']
currency char(3) [note: 'required for fixed']
max_discount_minor bigint
min_spend_minor bigint
starts_at timestamptz [not null]
ends_at timestamptz [not null]
usage_limit_total integer
usage_limit_per_user integer
first_booking_only boolean [not null, default: false]
funded_by discount_funder [not null, default: 'platform']
applies_to_all_hotels boolean [not null, default: true]
is_active boolean [not null, default: true]
created_by uuid
deleted_at timestamptz [note: 'soft delete (redemptions keep pointing here)']
created_at timestamptz [not null, default: `now()`]
updated_at timestamptz [not null, default: `now()`]

Note: '''
CHECK ((value > (0)::numeric))
CHECK ((ends_at > starts_at))
CHECK ((((discount_type = 'percent'::discount_type) AND (value <= (100)::numeric)) OR ((discount_type = 'fixed'::discount_type) AND (currency IS NOT NULL))))
'''
}

Table discount_code_hotels {
discount_code_id uuid [not null]
hotel_id uuid [not null]

indexes {
(discount_code_id, hotel_id) [pk]
}
}

Table discount_redemptions {
id uuid [pk, default: `gen_random_uuid()`]
discount_code_id uuid [not null]
booking_id uuid [not null, unique]
user_id uuid [not null]
amount_minor bigint [not null]
created_at timestamptz [not null, default: `now()`]

indexes {
(discount_code_id, user_id) [name: 'discount_redemptions_discount_code_id_user_id_idx']
}
}

Table tax_rules {
id uuid [pk, default: `gen_random_uuid()`]
country_code char(2) [not null, note: 'ISO 3166-1, e.g. VN']
name text [not null, note: '"VAT"']
calc_type tax_calc_type [not null]
value numeric(12,4) [not null, note: 'percent, or an amount in currency for fixed types']
currency char(3) [note: 'required for fixed types']
is_included boolean [not null, default: false, note: 'already inside the room price?']
applies_after_discount boolean [not null, default: true, note: 'percent only: tax on the discounted price']
valid_from date [note: 'null = no start limit']
valid_to date [note: 'null = no end limit (inclusive)']
is_active boolean [not null, default: true]
created_at timestamptz [not null, default: `now()`]
updated_at timestamptz [not null, default: `now()`]

indexes {
country_code [name: 'tax_rules_country_code_idx', note: 'partial: WHERE is_active']
}

Note: '''
Taxes by COUNTRY only (e.g. VAT in VN). One country can have several rules
(VAT + a fixed per-night fee); a new rate = a new row with new validity dates,
so past bookings keep the rate they were charged (copied to booking_taxes).
Hotel service charges are not tax rules: owners enter room prices that already
include their own service charge.
calc_type: percent -> value % of the room price (10 = 10%)
per_night -> value x rooms x nights
per_person_night -> value x guests x nights
per_booking -> value once per booking
CHECK ((country_code ~ '^[A-Z]{2}$'::text))
CHECK ((value > (0)::numeric))
CHECK (((calc_type <> 'percent'::tax_calc_type) OR (value <= (100)::numeric)))
CHECK (((calc_type = 'percent'::tax_calc_type) = (currency IS NULL)))
CHECK (((valid_to IS NULL) OR (valid_from IS NULL) OR (valid_to >= valid_from)))
'''
}

Table currencies {
code char(3) [pk, note: 'ISO 4217, e.g. USD, VND']
name text [not null]
symbol text [not null]
minor_unit smallint [not null, note: 'USD 2, VND 0']
is_enabled boolean [not null, default: true]
created_at timestamptz [not null, default: `now()`]
updated_at timestamptz [not null, default: `now()`]

Note: '''
CHECK (((minor_unit >= 0) AND (minor_unit <= 4)))
'''
}

Table exchange_rates {
base_currency char(3) [not null]
quote_currency char(3) [not null]
rate numeric(20,10) [not null]
effective_date date [not null]
fetched_at timestamptz [not null, default: `now()`]

indexes {
(base_currency, quote_currency, effective_date) [pk]
}

Note: '''
CHECK ((rate > (0)::numeric))
'''
}

// ------------------------------------------------------------------ approvals_and_activity

Table change_requests {
id uuid [pk, default: `gen_random_uuid()`]
partner_id uuid [not null]
hotel_id uuid
entity_type change_entity [not null]
entity_id uuid [note: 'null when action = create']
action change_action [not null]
payload jsonb [not null, note: 'proposed values']
previous jsonb [note: 'live values when submitted']
status change_status [not null, default: 'draft']
submitted_by uuid [not null]
submitted_at timestamptz
reviewed_by uuid
reviewed_at timestamptz
reject_reason text
created_at timestamptz [not null, default: `now()`]
updated_at timestamptz [not null, default: `now()`]

indexes {
submitted_at [name: 'admin_queue', note: 'partial: WHERE (status = \'pending\'::change_status)']
(hotel_id, status) [name: 'change_requests_hotel_id_status_idx']
(entity_type, entity_id) [name: 'one_open_request_per_entity', note: 'UNIQUE only WHERE ((status = ANY (ARRAY[\'draft\'::change_status, \'pending\'::change_status])) AND (entity_id IS NOT NULL))']
}

Note: '''
CHECK (((status <> 'rejected'::change_status) OR (reject_reason IS NOT NULL)))
'''
}

Table reviews {
id uuid [pk, default: `gen_random_uuid()`]
booking_id uuid [not null, unique]
hotel_id uuid [not null]
user_id uuid [not null]
score smallint [not null]
title text
body text
traveller_type text [note: 'solo, couple, family, business, group']
status review_status [not null, default: 'published']
owner_reply text
owner_replied_at timestamptz
created_at timestamptz [not null, default: `now()`]
updated_at timestamptz [not null, default: `now()`]

indexes {
(hotel_id, created_at) [name: 'reviews_hotel_id_created_at_idx', note: 'DESC on created_at; partial: WHERE (status = \'published\'::review_status)']
}

Note: '''
CHECK (((score >= 1) AND (score <= 10)))
'''
}

Table wishlists {
user_id uuid [not null]
hotel_id uuid [not null]
created_at timestamptz [not null, default: `now()`]

indexes {
(user_id, hotel_id) [pk]
}
}

Table recently_viewed_hotels {
user_id uuid [not null]
hotel_id uuid [not null]
viewed_at timestamptz [not null, default: `now()`]
view_count integer [not null, default: 1]

indexes {
(user_id, hotel_id) [pk]
(user_id, viewed_at) [name: 'recently_viewed_hotels_user_id_viewed_at_idx', note: 'DESC on viewed_at']
}

Note: '''
Recently viewed hotels for signed-in users (guests keep the list in the browser).
One row per user + hotel; viewing again moves it to the top (upsert on viewed_at).
'''
}

Table notifications {
id uuid [pk, default: `gen_random_uuid()`]
user_id uuid [not null]
type text [not null, note: 'booking_new, change_approved…']
title text [not null]
body text
data jsonb
read_at timestamptz
created_at timestamptz [not null, default: `now()`]

indexes {
(user_id, created_at) [name: 'notifications_user_id_created_at_idx', note: 'DESC on created_at']
}
}

Table email_outbox {
id uuid [pk, default: `gen_random_uuid()`]
to_email citext [not null]
template text [not null, note: 'booking_confirmed, password_reset…']
payload jsonb [not null]
status outbox_status [not null, default: 'queued']
attempts smallint [not null, default: 0]
last_error text
sent_at timestamptz
created_at timestamptz [not null, default: `now()`]

indexes {
created_at [name: 'outbox_queue', note: 'partial: WHERE (status = \'queued\'::outbox_status)']
}
}

Table audit_logs {
id bigint [pk, increment]
actor_user_id uuid
action text [not null, note: 'change_request.approve, booking.cancel…']
entity_type text [not null]
entity_id uuid
before jsonb
after jsonb
ip inet
created_at timestamptz [not null, default: `now()`]

indexes {
(entity_type, entity_id) [name: 'audit_logs_entity_type_entity_id_idx']
}
}

Table payouts {
id uuid [pk, default: `gen_random_uuid()`]
partner_id uuid [not null]
period_start date [not null]
period_end date [not null]
currency char(3) [not null]
gross_minor bigint [not null]
commission_minor bigint [not null]
refunds_minor bigint [not null, default: 0]
net_minor bigint [not null]
status payout_status [not null, default: 'draft']
paid_at timestamptz
created_at timestamptz [not null, default: `now()`]

indexes {
(partner_id, period_start, period_end, currency) [unique]
}
}

Table payout_items {
payout_id uuid [not null]
booking_id uuid [not null]
amount_minor bigint [not null]

indexes {
(payout_id, booking_id) [pk]
booking_id [unique, name: 'booking_paid_out_once']
}
}

// ------------------------------------------------------------------ Relationships
// >  many-to-one,  -  one-to-one (FK column is unique)

Ref: users.preferred_currency > currencies.code
Ref: user_roles.user_id > users.id [delete: cascade]
Ref: auth_identities.user_id > users.id [delete: cascade]
Ref: user_tokens.user_id > users.id [delete: cascade]
Ref: user_sessions.user_id > users.id [delete: cascade]
Ref: partners.approved_by > users.id
Ref: partner_members.partner_id > partners.id [delete: cascade]
Ref: partner_members.user_id > users.id [delete: cascade]
Ref: partner_documents.partner_id > partners.id [delete: cascade]
Ref: partner_documents.uploaded_by > users.id
Ref: hotels.accommodation_type_id > accommodation_types.id
Ref: addresses.id - hotels.address_id
Ref: hotels.currency > currencies.code
Ref: hotels.partner_id > partners.id
Ref: hotel_photos.hotel_id > hotels.id [delete: cascade]
Ref: hotel_photos.room_type_id > room_types.id [delete: cascade]
Ref: hotel_policies.hotel_id > hotels.id [delete: cascade]
Ref: hotel_amenities.amenity_id > amenities.id
Ref: hotel_amenities.hotel_id > hotels.id [delete: cascade]
Ref: room_types.hotel_id > hotels.id [delete: cascade]
Ref: rooms.hotel_id > hotels.id [delete: cascade]
Ref: rooms.(room_type_id, hotel_id) > room_types.(id, hotel_id) [delete: cascade]
Ref: room_type_beds.bed_type_id > bed_types.id
Ref: room_type_beds.room_type_id > room_types.id [delete: cascade]
Ref: room_type_amenities.amenity_id > amenities.id
Ref: room_type_amenities.room_type_id > room_types.id [delete: cascade]
Ref: rate_plans.board_type_id > board_types.id
Ref: rate_plans.cancellation_policy_id > cancellation_policies.id
Ref: rate_plans.currency > currencies.code
Ref: rate_plans.room_type_id > room_types.id [delete: cascade]
Ref: cancellation_policies.hotel_id > hotels.id [delete: cascade]
Ref: cancellation_policy_rules.policy_id > cancellation_policies.id [delete: cascade]
Ref: room_inventory.room_type_id > room_types.id [delete: cascade]
Ref: rate_plan_prices.rate_plan_id > rate_plans.id [delete: cascade]
Ref: rate_rules.rate_plan_id > rate_plans.id [delete: cascade]
Ref: carts.user_id > users.id [delete: cascade]
Ref: cart_items.cart_id > carts.id [delete: cascade]
Ref: cart_items.currency > currencies.code
Ref: cart_items.rate_plan_id > rate_plans.id [delete: cascade]
Ref: bookings.cancelled_by > users.id
Ref: bookings.currency > currencies.code
Ref: bookings.discount_code_id > discount_codes.id
Ref: bookings.display_currency > currencies.code
Ref: bookings.hotel_id > hotels.id
Ref: bookings.user_id > users.id
Ref: booking_rooms.booking_id > bookings.id [delete: cascade]
Ref: booking_rooms.rate_plan_id > rate_plans.id
Ref: booking_rooms.room_type_id > room_types.id
Ref: room_assignments.assigned_by > users.id
Ref: room_assignments.(booking_room_id, room_type_id) > booking_rooms.(id, room_type_id) [delete: cascade]
Ref: room_assignments.(room_id, room_type_id) > rooms.(id, room_type_id)
Ref: booking_room_nights.booking_room_id > booking_rooms.id [delete: cascade]
Ref: booking_taxes.booking_id > bookings.id [delete: cascade]
Ref: booking_taxes.tax_rule_id > tax_rules.id
Ref: booking_status_history.booking_id > bookings.id [delete: cascade]
Ref: booking_status_history.changed_by > users.id
Ref: payments.booking_id > bookings.id
Ref: payments.currency > currencies.code
Ref: refunds.booking_id > bookings.id
Ref: refunds.currency > currencies.code
Ref: refunds.initiated_by > users.id
Ref: refunds.payment_id > payments.id
Ref: discount_codes.created_by > users.id
Ref: discount_codes.currency > currencies.code
Ref: discount_code_hotels.discount_code_id > discount_codes.id [delete: cascade]
Ref: discount_code_hotels.hotel_id > hotels.id [delete: cascade]
Ref: bookings.id - discount_redemptions.booking_id [delete: cascade]
Ref: discount_redemptions.discount_code_id > discount_codes.id
Ref: discount_redemptions.user_id > users.id
Ref: tax_rules.currency > currencies.code
Ref: exchange_rates.base_currency > currencies.code
Ref: exchange_rates.quote_currency > currencies.code
Ref: change_requests.hotel_id > hotels.id [delete: cascade]
Ref: change_requests.partner_id > partners.id
Ref: change_requests.reviewed_by > users.id
Ref: change_requests.submitted_by > users.id
Ref: bookings.id - reviews.booking_id
Ref: reviews.hotel_id > hotels.id
Ref: reviews.user_id > users.id
Ref: wishlists.hotel_id > hotels.id [delete: cascade]
Ref: wishlists.user_id > users.id [delete: cascade]
Ref: recently_viewed_hotels.hotel_id > hotels.id [delete: cascade]
Ref: recently_viewed_hotels.user_id > users.id [delete: cascade]
Ref: notifications.user_id > users.id [delete: cascade]
Ref: audit_logs.actor_user_id > users.id
Ref: payouts.currency > currencies.code
Ref: payouts.partner_id > partners.id
Ref: payout_items.booking_id > bookings.id
Ref: payout_items.payout_id > payouts.id [delete: cascade]

// ------------------------------------------------------------------ Groups (boxes in dbdiagram)

TableGroup users_and_partners {
users
user_roles
auth_identities
user_tokens
user_sessions
partners
partner_members
partner_documents
}

TableGroup hotel_catalog {
destinations
addresses
hotels
hotel_photos
hotel_policies
amenities
hotel_amenities
accommodation_types
}

TableGroup rooms_rates_availability {
room_types
rooms
room_type_beds
bed_types
room_type_amenities
rate_plans
board_types 
cancellation_policies
cancellation_policy_rules
room_inventory
rate_plan_prices
rate_rules
}

TableGroup cart_and_booking {
carts
cart_items
bookings
booking_rooms
room_assignments
booking_room_nights
booking_taxes
booking_status_history
}

TableGroup payments_discounts_taxes {
payments
refunds
stripe_events
discount_codes
discount_code_hotels
discount_redemptions
tax_rules
currencies
exchange_rates
}

TableGroup approvals_and_activity {
change_requests
reviews
wishlists
recently_viewed_hotels
notifications
email_outbox
audit_logs
payouts
payout_items
}