-- set innodb lock wait timeout
SET SESSION innodb_lock_wait_timeout = 7200;

INSERT INTO sys_module (id, organization_id, module_key, enable, pos, create_user, create_time, update_user, update_time)
VALUES (UUID_SHORT(), '100001', 'mmbaAudit', true, 12,
        'admin', UNIX_TIMESTAMP() * 1000, 'admin', UNIX_TIMESTAMP() * 1000);

INSERT INTO sys_role_permission (id, role_id, permission_id)
VALUES (UUID_SHORT(), 'org_admin', 'MMBA_AUDIT:READ');

INSERT INTO sys_role ( id, NAME, internal, data_scope, create_time, update_time, create_user, update_user, description, organization_id )
VALUES
    ( 'mmba_audit_manager', 'mmba_audit_manager', 1, 'ALL', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'admin', 'admin', '', '100001' );

INSERT INTO sys_role_permission (id, role_id, permission_id)
VALUES (UUID_SHORT(), 'mmba_audit_manager', 'MMBA_AUDIT:READ');

SET SESSION innodb_lock_wait_timeout = DEFAULT;
