-- init global account pool search permission for organization administrator
INSERT INTO sys_role_permission (id, role_id, permission_id)
VALUES (UUID_SHORT(), 'org_admin', 'CUSTOMER_MANAGEMENT_POOL_GLOBAL:READ');
