-- V1_001_create_test_hearing_table.sql
-- Initial schema (first-time install) for Case Documents AI Responses
-- Postgres 14+
-- Idempotent via IF NOT EXISTS; no ALTER/PL/pgSQL guards included.

BEGIN;

-- ===================================================================
-- Canonical queries table
-- ===================================================================
CREATE TABLE IF NOT EXISTS test_hearing (
  hearing_id    UUID        PRIMARY KEY,
  payload       TEXT NOT NULL
);
COMMENT ON TABLE test_hearing IS 'Hearing data as a payload';




COMMIT;
