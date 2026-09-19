CREATE TABLE IF NOT EXISTS hello_counter (
    id BIGINT NOT NULL,
    count BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hello_counter PRIMARY KEY (id)
);
