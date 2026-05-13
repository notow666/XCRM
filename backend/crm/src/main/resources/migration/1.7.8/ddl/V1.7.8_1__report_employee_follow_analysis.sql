ALTER TABLE follow_up_record
    ADD INDEX idx_follow_up_record_time_owner_customer (follow_time, owner, customer_id);

ALTER TABLE mmba_call_record_audit
    ADD INDEX idx_mmba_call_customer_tel_begin_ts (customer_tel, begin_timestamp);

ALTER TABLE mmba_wx_friend_change_audit
    ADD INDEX idx_mmba_wx_friend_change_um_ts (um, timestamp),
    ADD INDEX idx_mmba_wx_friend_change_friend_phone_ts (friend_phone, timestamp);

CREATE TABLE report_employee_follow_analysis_fact_day
(
    id                     VARCHAR(32) NOT NULL COMMENT '主键ID',
    stat_date              DATE        NOT NULL COMMENT '统计日期',
    customer_id            VARCHAR(32) NOT NULL COMMENT '客户ID',
    operator_user_id       VARCHAR(32) NOT NULL COMMENT '行为员工ID',
    inbound_customer_flag  TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '入库客户标记',
    contacted_customer_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '联系客户标记',
    new_wechat_friend_flag TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '新增微信好友标记',
    dial_count             INT         NOT NULL DEFAULT 0 COMMENT '拨打电话数',
    connected_count        INT         NOT NULL DEFAULT 0 COMMENT '拨打接通数',
    call_over_1min_count   INT         NOT NULL DEFAULT 0 COMMENT '一分钟以上通话数',
    call_over_3min_count   INT         NOT NULL DEFAULT 0 COMMENT '三分钟以上通话数',
    call_duration_sec      BIGINT      NOT NULL DEFAULT 0 COMMENT '接通通话时长（秒）',
    create_user            VARCHAR(32) NOT NULL COMMENT '创建人',
    create_time            BIGINT      NOT NULL COMMENT '创建时间',
    update_user            VARCHAR(32) NOT NULL COMMENT '更新人',
    update_time            BIGINT      NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_report_emp_follow_fact_day (stat_date, customer_id, operator_user_id),
    KEY idx_report_emp_follow_fact_day_stat_date (stat_date),
    KEY idx_report_emp_follow_fact_day_operator (operator_user_id),
    KEY idx_report_emp_follow_fact_day_customer (customer_id)
) COMMENT='员工跟进分析事实日报表'
ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_general_ci;
