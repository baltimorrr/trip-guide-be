-- Hotels a customer saved to compare and book later (C-SRCH-08). Table and column names follow DATABASE-SCHEMA.md.
-- Like user_roles, the composite key replaces id/version. created_at is written by the application: rows are added
-- by one idempotent insert (see WishlistRepositoryImpl), which Spring Data auditing doesn't see.
create table wishlists (
    user_id    bigint      not null references users (id) on delete cascade,
    hotel_id   bigint      not null references hotels (id) on delete cascade,
    created_at timestamptz not null,
    primary key (user_id, hotel_id)
);
