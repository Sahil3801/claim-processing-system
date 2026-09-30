-- Optimistic concurrency control: a claim update only commits if nobody else changed
-- the row since it was read, so two concurrent officer decisions cannot both win.
ALTER TABLE claims ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
