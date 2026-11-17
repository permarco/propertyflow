-- Prüfnachweis und Audit sind getrennt: Identitätsbelege gehören nicht ins Aktionsaudit.
ALTER TABLE pf_access_verification ADD COLUMN verified_at timestamptz;
UPDATE pf_access_verification SET verified_at = consumed_at WHERE consumed_at IS NOT NULL;
CREATE TABLE pf_access_audit (
    id uuid PRIMARY KEY REFERENCES pf_access_verification(id),
    case_id varchar(80) NOT NULL,
    actor_id uuid NOT NULL,
    action varchar(32) NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE pf_account_audit ADD COLUMN operation varchar(80);
CREATE UNIQUE INDEX uk_pf_account_audit_operation ON pf_account_audit (actor_id, target_id, operation) WHERE operation IS NOT NULL;
