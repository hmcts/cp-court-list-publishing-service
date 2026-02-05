-- V1001__recreate_court_list_publish_status_table.sql
-- Drop and recreate table (test data only). Removes any unwanted CHECK constraints
-- and produces the final schema matching CourtListStatusEntity.

DROP TABLE IF EXISTS court_list_publish_status;

CREATE TABLE court_list_publish_status (
    court_list_id         UUID                     PRIMARY KEY,
    court_centre_id       UUID                     NOT NULL,
    publish_status        TEXT                     NOT NULL,
    file_status           TEXT                     NOT NULL,
    court_list_type       TEXT                     NOT NULL,
    last_updated          TIMESTAMP WITH TIME ZONE NOT NULL,
    file_url              TEXT,
    publish_error_message TEXT,
    file_error_message    TEXT,
    publish_date          DATE                     NOT NULL
);
