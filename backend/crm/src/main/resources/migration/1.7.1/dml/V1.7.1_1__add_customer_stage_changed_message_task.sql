SET @customer_stage_changedID = UUID_SHORT();
INSERT INTO sys_message_task (id, event, task_type, email_enable, sys_enable, display, organization_id,
                              template, create_user, create_time, update_user, update_time)
    VALUE (@customer_stage_changedID, 'CUSTOMER_STAGE_CHANGED', 'CUSTOMER', false, true, true, '100001',
    null, 'admin', UNIX_TIMESTAMP() * 1000 + 2, 'admin', UNIX_TIMESTAMP() * 1000 + 2 );

SET @customer_stage_completedID = UUID_SHORT();
INSERT INTO sys_message_task (id, event, task_type, email_enable, sys_enable, display, organization_id,
                              template, create_user, create_time, update_user, update_time)
    VALUE (@customer_stage_completedID, 'CUSTOMER_STAGE_COMPLETED', 'CUSTOMER', false, true, true, '100001',
    null, 'admin', UNIX_TIMESTAMP() * 1000 + 2, 'admin', UNIX_TIMESTAMP() * 1000 + 2 );

SET @customer_stage_failedID = UUID_SHORT();
INSERT INTO sys_message_task (id, event, task_type, email_enable, sys_enable, display, organization_id,
                              template, create_user, create_time, update_user, update_time)
    VALUE (@customer_stage_failedID, 'CUSTOMER_STAGE_FAILED', 'CUSTOMER', false, true, true, '100001',
    null, 'admin', UNIX_TIMESTAMP() * 1000 + 2, 'admin', UNIX_TIMESTAMP() * 1000 + 2 );

update sys_message_task set display = false where event = 'CUSTOMER_CONCAT_ADD';
update sys_message_task set display = false where event = 'CUSTOMER_COLLABORATION_ADD';
update sys_message_task set display = false where event = 'CLUE_AUTOMATIC_MOVE_POOL';
update sys_message_task set display = false where event = 'CLUE_MOVED_POOL';
update sys_message_task set display = false where event = 'CLUE_CONVERT_BUSINESS';
update sys_message_task set display = false where event = 'TRANSFER_CLUE';
update sys_message_task set display = false where event = 'CLUE_DISTRIBUTED';
update sys_message_task set display = false where event = 'CLUE_IMPORT';
update sys_message_task set display = false where event = 'CLUE_FOLLOW_UP_PLAN_DUE';

update sys_message_task set display = false where task_type = 'OPPORTUNITY';
