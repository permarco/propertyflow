CREATE TABLE pf_staff_account (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    email VARCHAR(254) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL,
    staff_role BOOLEAN NOT NULL,
    account_administration BOOLEAN NOT NULL,
    CONSTRAINT ck_pf_staff_account_name CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_pf_staff_account_email CHECK (email = lower(email)),
    CONSTRAINT ck_pf_staff_account_admin_role CHECK (NOT account_administration OR staff_role)
);

-- Serializes setup and records the server-side creation and role assignment.
CREATE TABLE pf_staff_setup (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    account_id UUID REFERENCES pf_staff_account (id),
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_pf_staff_setup_completion CHECK ((account_id IS NULL) = (completed_at IS NULL))
);

INSERT INTO pf_staff_setup (id) VALUES (1);
