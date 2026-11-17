-- Bestehende Konten bleiben erhalten; Zusatzrechte werden ausdrücklich verwaltet.
CREATE TABLE pf_tenant_account (
    id uuid PRIMARY KEY,
    email varchar(254) NOT NULL UNIQUE CHECK (email = lower(email)),
    active boolean NOT NULL DEFAULT true,
    verified_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE pf_staff_account ADD COLUMN case_access_administration boolean NOT NULL DEFAULT false;
ALTER TABLE pf_staff_account ADD COLUMN version bigint NOT NULL DEFAULT 0;
-- Das bei der Servereinrichtung bestimmte Verwaltungskonto übernimmt die Erstverwaltung.
UPDATE pf_staff_account SET case_access_administration = true WHERE account_administration AND active AND staff_role;
CREATE TABLE pf_account_audit (
    id uuid PRIMARY KEY,
    actor_id uuid NOT NULL,
    target_id uuid NOT NULL,
    action varchar(40) NOT NULL,
    detail text NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE pf_tenant_message ADD COLUMN actor_account_id uuid;
CREATE INDEX ix_pf_case_contact_email ON pf_case (contact_email);
