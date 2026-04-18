-- =====================================================================
-- V000__schema_migrations.sql  (applies before V001)
-- Creates the history table used by MigrationRunner to decide what to skip.
-- Must be idempotent: executed every startup before migration discovery.
-- =====================================================================
CREATE TABLE IF NOT EXISTS schema_migrations (
    version     VARCHAR(20)  PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    checksum    CHAR(64)     NOT NULL,
    applied_at  TIMESTAMP    NOT NULL,
    success     BOOLEAN      NOT NULL
);
