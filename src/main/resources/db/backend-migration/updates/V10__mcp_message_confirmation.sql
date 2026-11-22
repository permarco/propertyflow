CREATE TABLE pf_mcp_confirmation (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL,
    delegation_id uuid NOT NULL,
    client_id varchar(100) NOT NULL,
    scope varchar(40) NOT NULL,
    case_id varchar(80) NOT NULL,
    case_version bigint NOT NULL,
    case_title text NOT NULL,
    message_text text NOT NULL,
    answer_required boolean NOT NULL,
    expires_at timestamptz NOT NULL,
    status varchar(24) NOT NULL CHECK (status IN ('WAITING', 'SUBMITTED', 'SENT', 'CANCELLED', 'REJECTED')),
    message_id uuid
);
CREATE INDEX pf_mcp_confirmation_expiry_idx ON pf_mcp_confirmation (expires_at);

CREATE TABLE pf_mcp_audit (
    id uuid PRIMARY KEY,
    occurred_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    channel varchar(10) NOT NULL DEFAULT 'mcp' CHECK (channel = 'mcp'),
    action varchar(40) NOT NULL,
    account_id uuid,
    client_id varchar(100),
    case_id varchar(80),
    operation_id uuid,
    outcome varchar(40) NOT NULL
);
CREATE INDEX pf_mcp_audit_expiry_idx ON pf_mcp_audit (occurred_at);
CREATE INDEX pf_mcp_audit_operation_idx ON pf_mcp_audit (operation_id, occurred_at);
