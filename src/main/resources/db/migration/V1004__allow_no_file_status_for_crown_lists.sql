-- V1004__allow_no_file_status_for_crown_lists.sql
--
-- Crown court lists published through POST /api/court-list-publish/crown/publish (FINAL, FIRM and WARN)
-- are sent to CaTH as JSON and produce no file, like SJP lists. Their status rows therefore have no file
-- status. This widens the V1003 check, which allowed a missing file status only for SJP rows, to these
-- list types. Every other list type still requires a file status.
--
-- The check is by list type because the crown endpoint is the only writer of FINAL, FIRM and WARN rows:
-- POST /api/court-list-publish/publish rejects those types and points callers to the crown endpoint.

ALTER TABLE court_list_publish_status
    DROP CONSTRAINT ck_file_status_required_for_non_sjp;

ALTER TABLE court_list_publish_status
    ADD CONSTRAINT ck_file_status_required_for_non_sjp_non_crown
    CHECK (file_status IS NOT NULL
        OR starts_with(court_list_type, 'SJP_')
        OR court_list_type IN ('FINAL', 'FIRM', 'WARN'));
