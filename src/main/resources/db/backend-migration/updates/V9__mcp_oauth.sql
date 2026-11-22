-- Spring Authorization Server verwendet diese Tabellen für Freigaben und widerrufbare Tokens.
CREATE TABLE oauth2_authorization (
    id varchar(100) PRIMARY KEY,
    registered_client_id varchar(100) NOT NULL,
    principal_name varchar(200) NOT NULL,
    authorization_grant_type varchar(100) NOT NULL,
    authorized_scopes varchar(1000),
    attributes text,
    state varchar(500),
    authorization_code_value text,
    authorization_code_issued_at timestamptz,
    authorization_code_expires_at timestamptz,
    authorization_code_metadata text,
    access_token_value text,
    access_token_issued_at timestamptz,
    access_token_expires_at timestamptz,
    access_token_metadata text,
    access_token_type varchar(100),
    access_token_scopes varchar(1000),
    oidc_id_token_value text,
    oidc_id_token_issued_at timestamptz,
    oidc_id_token_expires_at timestamptz,
    oidc_id_token_metadata text,
    refresh_token_value text,
    refresh_token_issued_at timestamptz,
    refresh_token_expires_at timestamptz,
    refresh_token_metadata text,
    user_code_value text,
    user_code_issued_at timestamptz,
    user_code_expires_at timestamptz,
    user_code_metadata text,
    device_code_value text,
    device_code_issued_at timestamptz,
    device_code_expires_at timestamptz,
    device_code_metadata text
);

CREATE INDEX oauth2_authorization_principal_idx ON oauth2_authorization (principal_name, registered_client_id);
CREATE INDEX oauth2_authorization_access_token_idx ON oauth2_authorization (access_token_value);
CREATE INDEX oauth2_authorization_code_idx ON oauth2_authorization (authorization_code_value);
CREATE INDEX oauth2_authorization_state_idx ON oauth2_authorization (state);
CREATE INDEX oauth2_authorization_refresh_token_idx ON oauth2_authorization (refresh_token_value);

CREATE TABLE oauth2_authorization_consent (
    registered_client_id varchar(100) NOT NULL,
    principal_name varchar(200) NOT NULL,
    authorities varchar(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);
