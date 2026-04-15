-- set innodb lock wait timeout
SET SESSION innodb_lock_wait_timeout = 7200;

-- init sales manager pool import permission
INSERT INTO sys_role_permission (id, role_id, permission_id)
VALUES (UUID_SHORT(), 'sales_manager', 'CUSTOMER_MANAGEMENT_POOL:IMPORT');

-- init org_admin pool import permission
INSERT INTO sys_role_permission (id, role_id, permission_id)
VALUES (UUID_SHORT(), 'org_admin', 'CUSTOMER_MANAGEMENT_POOL:IMPORT');

-- init sales manager pool transfer permission
INSERT INTO sys_role_permission (id, role_id, permission_id)
VALUES (UUID_SHORT(), 'sales_manager', 'CUSTOMER_MANAGEMENT_POOL:TRANSFER');

-- init org_admin pool transfer permission
INSERT INTO sys_role_permission (id, role_id, permission_id)
VALUES (UUID_SHORT(), 'org_admin', 'CUSTOMER_MANAGEMENT_POOL:TRANSFER');

SET SESSION innodb_lock_wait_timeout = DEFAULT;