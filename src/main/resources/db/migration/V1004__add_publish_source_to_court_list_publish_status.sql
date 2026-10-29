-- Crown publishes (POST /crown/publish) get their own rows so they no longer collide with the standard publish flow
-- for the same court centre, date and court list type. A Crown publish produces no PDF, so file_status may be NULL.
ALTER TABLE court_list_publish_status
    ADD COLUMN publish_source TEXT NOT NULL DEFAULT 'STANDARD';

DROP INDEX IF EXISTS ux_court_list_publish_status_non_sjp;
CREATE UNIQUE INDEX IF NOT EXISTS ux_court_list_publish_status_non_sjp
    ON court_list_publish_status (court_centre_id, publish_date, court_list_type, publish_source)
    WHERE court_centre_id IS NOT NULL;

ALTER TABLE court_list_publish_status DROP CONSTRAINT IF EXISTS ck_file_status_required_for_non_sjp;
ALTER TABLE court_list_publish_status
    ADD CONSTRAINT ck_file_status_required_for_non_sjp
    CHECK (file_status IS NOT NULL OR starts_with(court_list_type, 'SJP_') OR publish_source = 'CROWN');
