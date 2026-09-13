ALTER TABLE users
ADD COLUMN nid_number VARCHAR(100) NULL,
ADD COLUMN guardian_name VARCHAR(120) NULL,
ADD COLUMN guardian_phone VARCHAR(30) NULL;

ALTER TABLE donor_profiles
ADD COLUMN height_cm DECIMAL(5,2) NULL,
ADD COLUMN chronic_conditions VARCHAR(500) DEFAULT '',
ADD COLUMN recent_surgery BOOLEAN DEFAULT FALSE,
ADD COLUMN recent_surgery_details VARCHAR(500) DEFAULT '',
ADD COLUMN recent_tattoo BOOLEAN DEFAULT FALSE,
ADD COLUMN recent_tattoo_details VARCHAR(500) DEFAULT '',
ADD COLUMN current_medications BOOLEAN DEFAULT FALSE,
ADD COLUMN current_medications_details VARCHAR(500) DEFAULT '',
ADD COLUMN recent_illness BOOLEAN DEFAULT FALSE,
ADD COLUMN recent_illness_details VARCHAR(500) DEFAULT '',
ADD COLUMN recent_pregnancy BOOLEAN DEFAULT FALSE,
ADD COLUMN recent_pregnancy_details VARCHAR(500) DEFAULT '';
