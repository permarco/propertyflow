-- Initialschema des Moduls access.
-- Konsolidierter Endstand der früheren Migrationen V1 bis V38; keine Bestandsmigration.

CREATE TABLE pf_staff_account (
    id uuid NOT NULL,
    name text NOT NULL,
    email character varying(254) NOT NULL,
    active boolean NOT NULL,
    staff_role boolean NOT NULL,
    account_administration boolean NOT NULL,
    CONSTRAINT ck_pf_staff_account_admin_role CHECK (((NOT account_administration) OR staff_role)),
    CONSTRAINT ck_pf_staff_account_email CHECK (((email)::text = lower((email)::text))),
    CONSTRAINT ck_pf_staff_account_name CHECK ((length(TRIM(BOTH FROM name)) > 0)),
    CONSTRAINT pf_staff_account_email_key UNIQUE (email),
    CONSTRAINT pf_staff_account_pkey PRIMARY KEY (id)
);

CREATE TABLE pf_staff_setup (
    id integer NOT NULL,
    account_id uuid,
    completed_at timestamp with time zone,
    CONSTRAINT ck_pf_staff_setup_completion CHECK (((account_id IS NULL) = (completed_at IS NULL))),
    CONSTRAINT pf_staff_setup_id_check CHECK ((id = 1)),
    CONSTRAINT pf_staff_setup_pkey PRIMARY KEY (id)
);

-- Fremdschlüssel werden nach allen Tabellen angelegt, damit zyklische Fallbezüge zulässig sind.

ALTER TABLE ONLY pf_staff_setup
    ADD CONSTRAINT pf_staff_setup_account_id_fkey FOREIGN KEY (account_id) REFERENCES pf_staff_account(id);

-- Zusätzliche Suchindizes und bedingte Eindeutigkeitsregeln.

-- Genau ein Datensatz koordiniert die erstmalige Einrichtung der Mitarbeiterkonten.
INSERT INTO pf_staff_setup (id) VALUES (1);
