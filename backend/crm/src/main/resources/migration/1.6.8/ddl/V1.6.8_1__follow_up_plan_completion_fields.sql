ALTER TABLE follow_up_plan
ADD COLUMN completion_status VARCHAR(32) DEFAULT 'UNCOMPLETED',
ADD COLUMN completion_time BIGINT;