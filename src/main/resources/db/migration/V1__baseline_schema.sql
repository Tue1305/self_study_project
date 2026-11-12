-- V1 Baseline Schema (PostgreSQL)
-- Represents the pre-multi-tenant base tables.
-- With spring.flyway.baseline-on-migrate=true and baseline-version=1,
-- Flyway skips executing V1 on existing databases and marks V1 as the baseline.

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255),
    role VARCHAR(255) DEFAULT 'ROLE_USER',
    subscription_plan VARCHAR(255) DEFAULT 'None',
    subscription_status VARCHAR(255) DEFAULT 'INACTIVE',
    name VARCHAR(255),
    email VARCHAR(255),
    phone_number VARCHAR(255),
    specialty VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS services (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    price NUMERIC(38, 2) NOT NULL,
    duration_minutes INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS appointments (
    id BIGSERIAL PRIMARY KEY,
    customer_name VARCHAR(255) NOT NULL,
    customer_phone VARCHAR(255) NOT NULL,
    customer_email VARCHAR(255) NOT NULL,
    appointment_time TIMESTAMP(6) NOT NULL,
    notes VARCHAR(1000),
    service_id BIGINT NOT NULL REFERENCES services(id),
    nail_tech_id BIGINT REFERENCES users(id),
    status VARCHAR(255) DEFAULT 'PENDING'
);

CREATE TABLE IF NOT EXISTS customer (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS showcase_items (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(255) NOT NULL,
    badge_text VARCHAR(255),
    tech_name VARCHAR(255),
    duration VARCHAR(255),
    image_url TEXT NOT NULL
);
