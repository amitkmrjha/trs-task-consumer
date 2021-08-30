

DROP TABLE  IF EXISTS  wallet CASCADE;

CREATE TABLE IF NOT EXISTS public.wallet(
    user_id VARCHAR(255) NOT NULL,
    round_id VARCHAR(255),
    league_id VARCHAR(255),
    trs_type VARCHAR(255),
    amount INTEGER,
    trs_status VARCHAR(255),
    transaction_id VARCHAR(255),
    lastAccountBalance INTEGER,
    PRIMARY KEY (transaction_id));


--drop table if exists public.akka_projection_offset_store;

CREATE TABLE IF NOT EXISTS public.akka_projection_offset_store (
    projection_name VARCHAR(255) NOT NULL,
    projection_key VARCHAR(255) NOT NULL,
    current_offset VARCHAR(255) NOT NULL,
    manifest VARCHAR(4) NOT NULL,
    mergeable BOOLEAN NOT NULL,
    last_updated BIGINT NOT NULL,
    PRIMARY KEY(projection_name, projection_key)
    );

CREATE INDEX IF NOT EXISTS projection_name_index ON akka_projection_offset_store (projection_name);

CREATE TABLE IF NOT EXISTS public.akka_projection_management (
  projection_name VARCHAR(255) NOT NULL,
  projection_key VARCHAR(255) NOT NULL,
  paused BOOLEAN NOT NULL,
  last_updated BIGINT NOT NULL,
  PRIMARY KEY(projection_name, projection_key)
);
