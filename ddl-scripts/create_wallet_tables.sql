
CREATE TABLE IF NOT EXISTS public.wallet(
    user_id VARCHAR(255) NOT NULL,
    round_id VARCHAR(255),
    league_id VARCHAR(255),
    trs_type VARCHAR(255),
    amount INTEGER,
    trs_status VARCHAR(255),
    transaction_id VARCHAR(255),
    lastAccountBalance INTEGER,
    PRIMARY KEY (user_id));
