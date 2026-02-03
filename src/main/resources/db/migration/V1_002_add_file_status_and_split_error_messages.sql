-- V1_002__add_file_status_and_split_error_messages.sql

-- Add file_status column as NOT NULL (using DEFAULT for existing rows, then removing it)
ALTER TABLE court_list_publish_status 
ADD COLUMN file_status TEXT NOT NULL DEFAULT 'REQUESTED';

-- Remove the default so it behaves like publish_status (no default constraint)
ALTER TABLE court_list_publish_status 
ALTER COLUMN file_status DROP DEFAULT;

-- Add new error message columns
ALTER TABLE court_list_publish_status 
ADD COLUMN publish_error_message TEXT,
ADD COLUMN file_error_message TEXT;

-- Migrate existing error_message data to publish_error_message
-- This assumes existing errors are publish-related
UPDATE court_list_publish_status 
SET publish_error_message = error_message 
WHERE error_message IS NOT NULL;

-- Drop the old error_message column
ALTER TABLE court_list_publish_status 
DROP COLUMN error_message;

-- Rename file_name
ALTER TABLE court_list_publish_status
    RENAME COLUMN file_name TO file_url;
