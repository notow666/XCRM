SET SESSION innodb_lock_wait_timeout = 7200;

UPDATE `sys_module_field` SET `pos` = 5 WHERE `internal_key` = 'recordResult';
UPDATE `sys_module_field` SET `pos` = 6 WHERE `internal_key` = 'recordFailReason';
UPDATE `sys_module_field` SET `pos` = 7 WHERE `internal_key` = 'recordDescription';
UPDATE `sys_module_field` SET `pos` = 8 WHERE `internal_key` = 'recordAttachment';

UPDATE sys_module_field_blob SET prop = JSON_SET(prop, '$.fieldWidth', '0.5')
    where id = (select id from `sys_module_field` WHERE `internal_key` = 'recordCustomer');

UPDATE sys_module_field_blob SET prop = JSON_SET(prop, '$.fieldWidth', '0.5')
    where id = (select id from `sys_module_field` WHERE `internal_key` = 'recordOwner');

UPDATE sys_module_field_blob SET prop = JSON_SET(prop, '$.fieldWidth', '0.5')
    where id = (select id from `sys_module_field` WHERE `internal_key` = 'recordMethod');

UPDATE sys_module_field_blob SET prop = JSON_SET(prop, '$.fieldWidth', '0.5', '$.dateType', 'datetime', '$.dateDefaultType', 'current')
    where id = (select id from `sys_module_field` WHERE `internal_key` = 'recordTime');

UPDATE sys_module_field_blob SET prop = JSON_SET(prop, '$.fieldWidth', '0.5')
    where id = (select id from `sys_module_field` WHERE `internal_key` = 'recordResult');

UPDATE sys_module_field_blob SET prop = JSON_SET(prop, '$.fieldWidth', '0.5')
    where id = (select id from `sys_module_field` WHERE `internal_key` = 'recordFailReason');


UPDATE `sys_module_field` SET `pos` = 1 WHERE `internal_key` = 'planCustomer';
UPDATE `sys_module_field` SET `pos` = 2 WHERE `internal_key` = 'planOwner';
UPDATE `sys_module_field` SET `pos` = 3 WHERE `internal_key` = 'planNextStage';
UPDATE `sys_module_field` SET `pos` = 4 WHERE `internal_key` = 'planMethod';
UPDATE `sys_module_field` SET `pos` = 5 WHERE `internal_key` = 'planProcessor';
UPDATE `sys_module_field` SET `pos` = 6 WHERE `internal_key` = 'planStartTime';
UPDATE `sys_module_field` SET `pos` = 7 WHERE `internal_key` = 'planContent';
UPDATE `sys_module_field` SET `pos` = 8 WHERE `internal_key` = 'planAttachment';

UPDATE sys_module_field_blob SET prop = JSON_SET(prop, '$.fieldWidth', '0.5')
    where id = (select id from `sys_module_field` WHERE `internal_key` = 'planCustomer');

UPDATE sys_module_field_blob SET prop = JSON_SET(prop, '$.fieldWidth', '0.5')
    where id = (select id from `sys_module_field` WHERE `internal_key` = 'planOwner');

UPDATE sys_module_field_blob SET prop = JSON_SET(prop, '$.fieldWidth', '0.5')
    where id = (select id from `sys_module_field` WHERE `internal_key` = 'planNextStage');

UPDATE sys_module_field_blob SET prop = JSON_SET(prop, '$.fieldWidth', '0.5')
    where id = (select id from `sys_module_field` WHERE `internal_key` = 'planMethod');

UPDATE sys_module_field_blob SET prop = JSON_SET(prop, '$.fieldWidth', '0.5')
    where id = (select id from `sys_module_field` WHERE `internal_key` = 'planProcessor');

UPDATE sys_module_field_blob SET prop = JSON_SET(prop, '$.fieldWidth', '0.5', '$.dateType', 'datetime', '$.dateDefaultType', 'current')
    where id = (select id from `sys_module_field` WHERE `internal_key` = 'planStartTime');

SET SESSION innodb_lock_wait_timeout = DEFAULT;