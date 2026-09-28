-- BookMyRoom - MySQL 8.0.16+ script (CHECK constraints need 8.0.16 or newer)
-- Run:  mysql -u root -p < database.sql

CREATE DATABASE IF NOT EXISTS bookmyroom
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE bookmyroom;

-- ------------------------------------------------------------
-- Tables
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rooms (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    room_number     VARCHAR(10)    NOT NULL,
    room_type       VARCHAR(20)    NOT NULL,
    capacity        INT            NOT NULL,
    rate_per_night  DECIMAL(10,2)  NOT NULL,
    available       BIT(1)         NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    CONSTRAINT uk_rooms_room_number UNIQUE (room_number),
    CONSTRAINT chk_rooms_type     CHECK (room_type IN ('SINGLE', 'DOUBLE', 'DELUXE')),
    CONSTRAINT chk_rooms_capacity CHECK (capacity > 0),
    CONSTRAINT chk_rooms_rate     CHECK (rate_per_night > 0)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS guests (
    id       BIGINT        NOT NULL AUTO_INCREMENT,
    name     VARCHAR(100)  NOT NULL,
    email    VARCHAR(150)  NOT NULL,
    phone    VARCHAR(20)   NOT NULL,
    address  VARCHAR(255)  NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS bookings (
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    booking_reference  VARCHAR(40)    NOT NULL,
    guest_id           BIGINT         NOT NULL,
    room_id            BIGINT         NOT NULL,
    check_in_date      DATE           NOT NULL,
    check_out_date     DATE           NOT NULL,
    number_of_guests   INT            NOT NULL,
    total_cost         DECIMAL(12,2)  NOT NULL,
    status             VARCHAR(20)    NOT NULL,
    created_at         DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_bookings_reference UNIQUE (booking_reference),
    CONSTRAINT fk_bookings_guest FOREIGN KEY (guest_id) REFERENCES guests (id),
    CONSTRAINT fk_bookings_room  FOREIGN KEY (room_id)  REFERENCES rooms (id),
    CONSTRAINT chk_bookings_dates  CHECK (check_out_date > check_in_date),
    CONSTRAINT chk_bookings_guests CHECK (number_of_guests > 0),
    CONSTRAINT chk_bookings_status CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    INDEX idx_bookings_room_dates (room_id, status, check_in_date, check_out_date)
) ENGINE = InnoDB;

-- ------------------------------------------------------------
-- Sample data
-- ------------------------------------------------------------
INSERT INTO rooms (room_number, room_type, capacity, rate_per_night, available) VALUES
    ('101', 'DOUBLE', 2, 1500.00, 1),
    ('102', 'SINGLE', 1,  900.00, 1),
    ('103', 'DOUBLE', 2, 1500.00, 1),
    ('201', 'DELUXE', 4, 2500.00, 1),
    ('202', 'DELUXE', 3, 2200.00, 1);

INSERT INTO guests (name, email, phone, address) VALUES
    ('Karthik Kumar',   'karthik@example.com', '9876543210', 'Chennai'),
    ('Priya Nair',      'priya.nair@example.com', '9845012345', 'Kochi'),
    ('Arjun Reddy',     'arjun.reddy@example.com', '9700123456', 'Hyderabad'),
    ('Meena Sundaram',  'meena.s@example.com', '9944556677', 'Coimbatore'),
    ('Rahul Verma',     'rahul.verma@example.com', '9811223344', 'Bengaluru');

-- Sample bookings (November 2026). No two confirmed bookings overlap for the same room.
-- Room 101 has back-to-back stays: check-out on 8 Nov, next check-in on 8 Nov (allowed).
INSERT INTO bookings
    (booking_reference, guest_id, room_id, check_in_date, check_out_date, number_of_guests, total_cost, status)
VALUES
    ('BMR-10001', 1, 1, '2026-11-05', '2026-11-08', 2, 4500.00, 'CONFIRMED'),
    ('BMR-10002', 2, 1, '2026-11-08', '2026-11-10', 2, 3000.00, 'CONFIRMED'),
    ('BMR-10003', 3, 4, '2026-11-12', '2026-11-15', 3, 7500.00, 'CONFIRMED');
