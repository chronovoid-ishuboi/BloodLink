CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(190) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    district VARCHAR(80) NOT NULL,
    address VARCHAR(255) NOT NULL DEFAULT '',
    role ENUM('DONOR','REQUESTER','ADMIN') NOT NULL,
    approved BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_users_role_state (role, approved, active),
    INDEX idx_users_district (district)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS donor_profiles (
    user_id BIGINT PRIMARY KEY,
    blood_group ENUM('O_NEGATIVE','O_POSITIVE','A_NEGATIVE','A_POSITIVE','B_NEGATIVE','B_POSITIVE','AB_NEGATIVE','AB_POSITIVE') NOT NULL,
    birth_date DATE NOT NULL,
    weight_kg DECIMAL(5,2) NOT NULL,
    last_donation_date DATE NULL,
    availability_status ENUM('AVAILABLE','BUSY','OUT_OF_TOWN','MEDICAL_HOLD') NOT NULL DEFAULT 'BUSY',
    verified_donation_count INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_donor_weight CHECK (weight_kg BETWEEN 35 AND 250),
    CONSTRAINT chk_verified_donations CHECK (verified_donation_count >= 0),
    CONSTRAINT fk_donor_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_donor_matching (availability_status, blood_group, last_donation_date),
    INDEX idx_donor_donation_count (verified_donation_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS blood_requests (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    requester_id BIGINT NOT NULL,
    blood_group ENUM('O_NEGATIVE','O_POSITIVE','A_NEGATIVE','A_POSITIVE','B_NEGATIVE','B_POSITIVE','AB_NEGATIVE','AB_POSITIVE') NOT NULL,
    units_needed INT NOT NULL,
    urgency ENUM('NORMAL','URGENT','CRITICAL') NOT NULL,
    hospital_name VARCHAR(180) NOT NULL,
    district VARCHAR(80) NOT NULL,
    deadline DATE NOT NULL,
    notes TEXT NOT NULL,
    status ENUM('PENDING','MATCHED','ACCEPTED','DECLINED','FULFILLED','CANCELLED','ESCALATED') NOT NULL DEFAULT 'PENDING',
    accepted_donor_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_request_units CHECK (units_needed BETWEEN 1 AND 20),
    CONSTRAINT fk_request_requester FOREIGN KEY (requester_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_request_donor FOREIGN KEY (accepted_donor_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_request_queue (status, urgency, deadline),
    INDEX idx_request_group_district (blood_group, district),
    INDEX idx_request_requester (requester_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS request_matches (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_id BIGINT NOT NULL,
    donor_id BIGINT NOT NULL,
    match_score DECIMAL(6,2) NOT NULL,
    match_reason VARCHAR(500) NOT NULL,
    status ENUM('NOTIFIED','ACCEPTED','DECLINED','EXPIRED') NOT NULL DEFAULT 'NOTIFIED',
    matched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMP NULL,
    CONSTRAINT fk_match_request FOREIGN KEY (request_id) REFERENCES blood_requests(id) ON DELETE CASCADE,
    CONSTRAINT fk_match_donor FOREIGN KEY (donor_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_request_donor UNIQUE (request_id, donor_id),
    INDEX idx_match_donor_state (donor_id, status, matched_at),
    INDEX idx_match_request_score (request_id, match_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS request_status_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_id BIGINT NOT NULL,
    from_status ENUM('PENDING','MATCHED','ACCEPTED','DECLINED','FULFILLED','CANCELLED','ESCALATED') NULL,
    to_status ENUM('PENDING','MATCHED','ACCEPTED','DECLINED','FULFILLED','CANCELLED','ESCALATED') NOT NULL,
    changed_by BIGINT NULL,
    note VARCHAR(500) NOT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_history_request FOREIGN KEY (request_id) REFERENCES blood_requests(id) ON DELETE CASCADE,
    CONSTRAINT fk_history_actor FOREIGN KEY (changed_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_history_request_time (request_id, changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(140) NOT NULL,
    message VARCHAR(700) NOT NULL,
    type VARCHAR(40) NOT NULL,
    related_request_id BIGINT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_request FOREIGN KEY (related_request_id) REFERENCES blood_requests(id) ON DELETE CASCADE,
    INDEX idx_notification_inbox (user_id, is_read, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS donation_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    donor_id BIGINT NOT NULL,
    request_id BIGINT NULL UNIQUE,
    donation_date DATE NOT NULL,
    hospital_name VARCHAR(180) NOT NULL,
    blood_group ENUM('O_NEGATIVE','O_POSITIVE','A_NEGATIVE','A_POSITIVE','B_NEGATIVE','B_POSITIVE','AB_NEGATIVE','AB_POSITIVE') NOT NULL,
    units INT NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_donation_units CHECK (units BETWEEN 1 AND 20),
    CONSTRAINT fk_donation_donor FOREIGN KEY (donor_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_donation_request FOREIGN KEY (request_id) REFERENCES blood_requests(id) ON DELETE SET NULL,
    INDEX idx_donation_donor_date (donor_id, donation_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor_user_id BIGINT NULL,
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id BIGINT NULL,
    details VARCHAR(700) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_audit_time (created_at),
    INDEX idx_audit_entity (entity_type, entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
