CREATE TABLE pf_case (
    id VARCHAR(80) PRIMARY KEY,
    title VARCHAR(150) NOT NULL,
    original_unit VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    contact_email VARCHAR(254) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_tenant_contact_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL,
    initial_message_id UUID NOT NULL UNIQUE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_pf_case_status CHECK (status IN (
        'RECEIVED', 'UNDER_CLARIFICATION', 'WAITING_FOR_TENANT', 'STAFF_REVIEW_REQUIRED',
        'IN_PROGRESS', 'WAITING_FOR_EXTERNAL', 'WAITING_FOR_FOLLOW_UP', 'CLOSED'
    )),
    CONSTRAINT ck_pf_case_version CHECK (version >= 0)
);

-- The original description is the first public message, identified by initial_message_id.
-- No second copy of its text is required.
CREATE TABLE pf_case_access (
    id UUID PRIMARY KEY,
    case_id VARCHAR(80) NOT NULL REFERENCES pf_case (id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    encrypted_token TEXT NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_pf_case_access_id_case UNIQUE (id, case_id)
);

CREATE UNIQUE INDEX uk_pf_case_access_active_case
    ON pf_case_access (case_id) WHERE revoked_at IS NULL;

CREATE TABLE pf_submission (
    id UUID PRIMARY KEY,
    guest_scope UUID NOT NULL,
    operation_id VARCHAR(80) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    case_id VARCHAR(80) NOT NULL REFERENCES pf_case (id),
    access_id UUID NOT NULL,
    CONSTRAINT uk_pf_submission_scope_operation UNIQUE (guest_scope, operation_id),
    CONSTRAINT fk_pf_submission_original_access FOREIGN KEY (access_id, case_id)
        REFERENCES pf_case_access (id, case_id)
);

CREATE TABLE pf_process_start_order (
    id UUID PRIMARY KEY,
    case_id VARCHAR(80) NOT NULL UNIQUE REFERENCES pf_case (id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    state VARCHAR(32) NOT NULL DEFAULT 'PENDING'
);

CREATE TABLE pf_acceptance_mail_order (
    id UUID PRIMARY KEY,
    case_id VARCHAR(80) NOT NULL UNIQUE REFERENCES pf_case (id),
    access_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    state VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    CONSTRAINT fk_pf_acceptance_mail_original_access FOREIGN KEY (access_id, case_id)
        REFERENCES pf_case_access (id, case_id)
);
