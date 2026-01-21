-- V1_001__create_court_list_publish_status_table.sql
CREATE TABLE court_list_publish_status (
    court_list_id    UUID        PRIMARY KEY,
    court_centre_id  UUID        NOT NULL,
    publish_status   TEXT        NOT NULL,
    court_list_type  TEXT        NOT NULL,
    last_updated     TIMESTAMP WITH TIME ZONE NOT NULL,
    court_list_file_id UUID,
    file_name        TEXT,
    error_message    TEXT,
    publish_date     DATE
);

