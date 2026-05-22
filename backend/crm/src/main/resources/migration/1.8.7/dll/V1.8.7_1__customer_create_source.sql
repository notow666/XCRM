SET SESSION innodb_lock_wait_timeout = 7200;

UPDATE customer SET create_source = 'POOL_IMPORT' WHERE create_source is NULL;

SET SESSION innodb_lock_wait_timeout = DEFAULT;