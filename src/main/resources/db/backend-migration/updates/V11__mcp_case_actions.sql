ALTER TABLE pf_mcp_confirmation ALTER COLUMN case_id DROP NOT NULL;
ALTER TABLE pf_mcp_confirmation ADD COLUMN action_argument varchar(200);
ALTER TABLE pf_mcp_confirmation ADD COLUMN task_key bigint;
ALTER TABLE pf_mcp_confirmation ADD COLUMN review_token text;
