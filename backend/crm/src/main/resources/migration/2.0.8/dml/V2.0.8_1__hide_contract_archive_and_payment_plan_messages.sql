-- 合同归档、回款计划到期及即将到期功能不再对外配置，关闭现有任务并隐藏配置项。
UPDATE sys_message_task
SET email_enable = false,
    sys_enable = false,
    we_com_enable = false,
    ding_talk_enable = false,
    lark_enable = false,
    display = false,
    update_user = 'admin',
    update_time = UNIX_TIMESTAMP() * 1000
WHERE task_type = 'CONTRACT'
  AND event IN ('CONTRACT_ARCHIVED', 'CONTRACT_PAYMENT_EXPIRED', 'CONTRACT_PAYMENT_EXPIRING');
