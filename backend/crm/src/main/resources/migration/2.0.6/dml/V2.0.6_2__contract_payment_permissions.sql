INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT UUID_SHORT(), 'org_admin', 'CONTRACT_PAYMENT_RECORD:APPROVAL'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_role_permission
    WHERE role_id = 'org_admin'
      AND permission_id = 'CONTRACT_PAYMENT_RECORD:APPROVAL'
);

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT UUID_SHORT(), 'sales_manager', 'CONTRACT_PAYMENT_RECORD:APPROVAL'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_role_permission
    WHERE role_id = 'sales_manager'
      AND permission_id = 'CONTRACT_PAYMENT_RECORD:APPROVAL'
);
