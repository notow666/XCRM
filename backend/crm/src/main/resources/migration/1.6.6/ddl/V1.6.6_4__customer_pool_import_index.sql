-- 公海导入性能优化索引
-- 用于优化公海导入预检查中的手机号冲突查询
-- 
-- 查询条件：organization_id + in_shared_pool + pool_id + mobile
-- 现有索引 uk_organization_mobile (organization_id, mobile) 可以部分覆盖
-- 
-- 新增复合索引，用于快速定位组织内的公海池归属状态

-- 添加复合索引：组织ID + 公海池状态 + 公海池ID
-- 用于优化公海导入预检查中的冲突查询
-- 选择性分析：organization_id 高选择性，in_shared_pool 低选择性（布尔值），pool_id 中选择性
CREATE INDEX idx_customer_org_pool_status 
ON customer(organization_id, in_shared_pool, pool_id);

-- 说明：
-- 1. 该索引用于优化公海导入预检查中的手机号冲突查询
-- 2. 查询条件为：WHERE organization_id = ? AND (in_shared_pool = false OR (in_shared_pool = true AND pool_id != ?)) AND mobile IN (...)
-- 3. 由于 in_shared_pool 是布尔值，选择性较低，但配合 organization_id 和 pool_id 可以有效过滤
-- 4. 现有的 uk_organization_mobile 索引也能部分覆盖查询，可根据实际数据量选择是否使用此索引
