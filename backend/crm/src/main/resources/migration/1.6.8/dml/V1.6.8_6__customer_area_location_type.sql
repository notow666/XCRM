-- 更新客户表单中"地区城市"字段的locationType为CHINA_PC（跳过国家选择，直接从省市开始）
UPDATE sys_module_field_blob ffb
SET ffb.prop = JSON_REPLACE(ffb.prop, '$.locationType', 'CHINA_PC')
WHERE ffb.id IN (
    SELECT mf.id FROM sys_module_field mf
    WHERE mf.internal_key = 'customerArea'
);
