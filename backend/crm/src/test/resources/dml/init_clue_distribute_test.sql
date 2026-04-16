INSERT INTO sys_role (id, name, internal, data_scope, create_time, update_time, create_user, update_user, organization_id) VALUES
    ('dist_role_id', 'role_dist_test', 0, 'all', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin', '100001');
INSERT INTO sys_user_role (id, user_id, role_id, create_time, update_time, create_user, update_user) VALUES
    ('dist_user_role_id', 'admin', 'dist_role_id', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin');
INSERT INTO sys_department (id, name, organization_id, parent_id, pos, create_time, update_time, create_user, update_user, resource) VALUES
    ('dist_department_id', 'dist_department_test', '100001', 'NONE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin', '');
INSERT INTO sys_organization_user (id, user_id, organization_id, department_id, create_time, update_time, create_user, update_user) VALUES
    ('dist_organization_user_id', 'admin', '100001', 'dist_department_id', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin');

INSERT INTO customer_pool (id, scope_id, organization_id, name, owner_id, enable, auto, create_time, update_time, create_user, update_user) VALUES
    ('dist_cs_pool_id', '[\"role_id\", \"department_id\", \"admin\"]', '100001', 'dist_target_pool', 'admin', 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin');

INSERT INTO clue_pool (id, scope_id, organization_id, name, owner_id, enable, auto, distribute, create_time, update_time, create_user, update_user) VALUES
    ('dist_clue_pool_id', '[\"role_id\", \"department_id\", \"admin\"]', '100001', 'dist_src_pool', 'admin', 1, 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin');
INSERT INTO clue_pool_recycle_rule (id, pool_id, operator, `condition`, create_time, update_time, create_user, update_user) VALUES
    ('dist_recycle_rule_id', 'dist_clue_pool_id', 'and', '[]', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin');
INSERT INTO clue_pool_distribute_rule (id, clue_pool_id, customer_pool_id, operator, `condition`, create_time, update_time, create_user, update_user) VALUES
    ('dist_distribute_rule_id', 'dist_clue_pool_id', 'dist_cs_pool_id', 'and', '{\"column\":\"storageTime\",\"operator\":\"DYNAMICS\",\"value\":\"6,month\",\"scope\":[\"Created\"]}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin');
