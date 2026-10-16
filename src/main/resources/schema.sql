CREATE SEQUENCE IF NOT EXISTS guest_seq START WITH 1;

CREATE SEQUENCE IF NOT EXISTS reservation_seq START WITH 1;

CREATE TABLE IF NOT EXISTS guests (
    id              VARCHAR(20)  PRIMARY KEY,
    name            VARCHAR(50)  NOT NULL,
    email           VARCHAR(254) NOT NULL,
    points          INT          NOT NULL DEFAULT 0,
    completed_stays INT          NOT NULL DEFAULT 0,
    CONSTRAINT uq_guest_email UNIQUE (email),
    CONSTRAINT ck_guest_points CHECK (points >= 0),
    CONSTRAINT ck_guest_stays CHECK (completed_stays >= 0)
);

CREATE TABLE IF NOT EXISTS reservations (
    id                  VARCHAR(20)   PRIMARY KEY,
    guest_id            VARCHAR(20)   NOT NULL,
    room_number         INT           NOT NULL,
    room_type           VARCHAR(10)   NOT NULL,
    check_in            DATE          NOT NULL,
    check_out           DATE          NOT NULL,
    guests              INT           NOT NULL,
    promo_code          VARCHAR(30),
    status              VARCHAR(12)   NOT NULL,
    created_at_millis   BIGINT        NOT NULL,
    nights              INT           NOT NULL,
    room_charge         DECIMAL(12,2) NOT NULL,
    extra_guest_charge  DECIMAL(12,2) NOT NULL,
    discount            DECIMAL(12,2) NOT NULL,
    vat                 DECIMAL(12,2) NOT NULL,
    tourism_fee         DECIMAL(12,2) NOT NULL,
    total               DECIMAL(12,2) NOT NULL,
    applied_discounts   VARCHAR(500)  NOT NULL,
    refund_amount       DECIMAL(12,2),
    price_adjustment    DECIMAL(12,2),
    CONSTRAINT fk_reservation_guest FOREIGN KEY (guest_id) REFERENCES guests (id),
    CONSTRAINT ck_reservation_dates CHECK (check_in < check_out),
    CONSTRAINT ck_reservation_guests CHECK (guests >= 1),
    CONSTRAINT ck_reservation_status CHECK (status IN
        ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'CHECKED_OUT', 'CANCELLED', 'NO_SHOW', 'EXPIRED'))
);

CREATE INDEX IF NOT EXISTS idx_reservation_room_dates ON reservations (room_number, check_in, check_out);

CREATE INDEX IF NOT EXISTS idx_reservation_status ON reservations (status)
