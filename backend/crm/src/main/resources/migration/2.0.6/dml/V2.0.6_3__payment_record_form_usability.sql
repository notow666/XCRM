-- 做单人应使用人员选择器，同时为创收公式增加可直接参考的占位示例。
UPDATE sys_module_field
SET type = 'MEMBER',
    update_time = UNIX_TIMESTAMP() * 1000,
    update_user = 'admin'
WHERE internal_key = 'contractPaymentRecordDealPerson';

UPDATE sys_module_field_blob mfb
JOIN sys_module_field mf ON mf.id = mfb.id
SET mfb.prop = JSON_REMOVE(
        JSON_SET(mfb.prop,
                 '$.type', 'MEMBER',
                 '$.placeholder', '请选择做单人'),
        '$.formula',
        '$.defaultValueType'
    )
WHERE mf.internal_key = 'contractPaymentRecordDealPerson';

UPDATE sys_module_field_blob mfb
JOIN sys_module_field mf ON mf.id = mfb.id
SET mfb.prop = JSON_SET(
        mfb.prop,
        REPLACE(
            JSON_UNQUOTE(JSON_SEARCH(mfb.prop, 'one', 'paymentProductRevenueFormula', NULL, '$.subFields[*].internalKey')),
            '.internalKey',
            '.placeholder'
        ),
        '例如：回款金额-成本金额-杂费金额-返佣金额'
    )
WHERE mf.internal_key = 'contractPaymentRecordProducts'
  AND JSON_SEARCH(mfb.prop, 'one', 'paymentProductRevenueFormula', NULL, '$.subFields[*].internalKey') IS NOT NULL;
