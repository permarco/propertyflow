-- Ursprüngliche Kontaktangabe bleibt bei einer geprüften Korrektur nachvollziehbar.
ALTER TABLE pf_case ADD COLUMN original_contact_email varchar(254);
UPDATE pf_case SET original_contact_email = contact_email;
CREATE TABLE pf_access_verification (
    id uuid PRIMARY KEY,
    case_id varchar(80) NOT NULL REFERENCES pf_case(id),
    actor_id uuid NOT NULL,
    case_version bigint NOT NULL,
    previous_email varchar(254) NOT NULL,
    target_email varchar(254) NOT NULL,
    identity_evidence text NOT NULL,
    code_digest varchar(64) NOT NULL,
    expires_at timestamptz NOT NULL,
    attempts integer NOT NULL DEFAULT 0,
    consumed_at timestamptz,
    operation varchar(80),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE pf_access_mail_order (
    id uuid PRIMARY KEY REFERENCES pf_access_verification(id),
    case_id varchar(80) NOT NULL REFERENCES pf_case(id),
    state varchar(16) NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'UNCERTAIN')),
    next_attempt_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attempts integer NOT NULL DEFAULT 0
);
