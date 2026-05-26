CREATE TABLE report_employee_stat_event
(
    id                 VARCHAR(32)  NOT NULL COMMENT '主键ID',
    organization_id    VARCHAR(32)  NOT NULL COMMENT '组织ID',
    event_time         BIGINT       NOT NULL COMMENT '事件发生时间',
    stat_date          DATE         NOT NULL COMMENT '事件发生日期',
    metric_type        VARCHAR(32)  NOT NULL COMMENT '指标类型',
    event_type         VARCHAR(32)  NOT NULL COMMENT '事件类型',
    customer_id        VARCHAR(32)  NOT NULL COMMENT '客户ID',
    customer_name      VARCHAR(255) NOT NULL COMMENT '客户姓名快照',
    customer_mobile    VARCHAR(32)           DEFAULT NULL COMMENT '客户手机号快照',
    owner_user_id      VARCHAR(32)  NOT NULL COMMENT '客户负责人ID',
    owner_user_name    VARCHAR(64)  NOT NULL COMMENT '客户负责人姓名快照',
    owner_dept_id      VARCHAR(32)           DEFAULT NULL COMMENT '客户负责人部门ID快照',
    owner_dept_name    VARCHAR(128)          DEFAULT NULL COMMENT '客户负责人部门名称快照',
    operator_user_id   VARCHAR(32)  NOT NULL COMMENT '操作人ID',
    operator_user_name VARCHAR(64)  NOT NULL COMMENT '操作人姓名快照',
    operator_dept_id   VARCHAR(32)           DEFAULT NULL COMMENT '操作人部门ID快照',
    operator_dept_name VARCHAR(128)          DEFAULT NULL COMMENT '操作人部门名称快照',
    biz_trace_id       VARCHAR(64)           DEFAULT NULL COMMENT '业务链路标识',
    source_table_name  VARCHAR(128) NOT NULL COMMENT '来源表名',
    create_user        VARCHAR(32)  NOT NULL COMMENT '创建人',
    create_time        BIGINT       NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id)
) COMMENT='员工分析事件明细表'
ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_general_ci;

CREATE TABLE report_employee_stat_day
(
    id                       VARCHAR(32)  NOT NULL COMMENT '主键ID',
    organization_id          VARCHAR(32)  NOT NULL COMMENT '组织ID',
    stat_date                DATE         NOT NULL COMMENT '统计日期',
    owner_user_id            VARCHAR(32)  NOT NULL COMMENT '客户负责人ID',
    owner_user_name          VARCHAR(64)  NOT NULL COMMENT '客户负责人姓名快照',
    owner_dept_id            VARCHAR(32)           DEFAULT NULL COMMENT '客户负责人部门ID快照',
    owner_dept_name          VARCHAR(128)          DEFAULT NULL COMMENT '客户负责人部门名称快照',
    inbound_customer_count   INT          NOT NULL DEFAULT 0 COMMENT '入库客户数',
    contacted_customer_count INT          NOT NULL DEFAULT 0 COMMENT '联系客户数',
    dial_count               INT          NOT NULL DEFAULT 0 COMMENT '拨打数量',
    connected_count          INT          NOT NULL DEFAULT 0 COMMENT '拨打接通数量',
    call_duration_sec        BIGINT       NOT NULL DEFAULT 0 COMMENT '通话时长秒',
    call_over_1min_count     INT          NOT NULL DEFAULT 0 COMMENT '一分钟以上通话数',
    call_over_3min_count     INT          NOT NULL DEFAULT 0 COMMENT '三分钟以上通话数',
    new_wechat_friend_count  INT          NOT NULL DEFAULT 0 COMMENT '新增微信好友数量',
    create_user              VARCHAR(32)  NOT NULL COMMENT '创建人',
    create_time              BIGINT       NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_org_date_owner (organization_id, stat_date, owner_user_id)
) COMMENT='员工分析日报汇总表'
ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_general_ci;
