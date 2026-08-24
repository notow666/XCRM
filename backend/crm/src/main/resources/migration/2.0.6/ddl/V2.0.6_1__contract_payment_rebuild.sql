-- 上门、签约为不可改名、删除或拖动的系统业务节点。
UPDATE customer_stage_config
SET is_fixed = b'1'
WHERE id IN ('stage_visit', 'stage_sign');

-- 合同主表保存当前生效投影，审批中的修改保存到版本表。
ALTER TABLE contract
    MODIFY COLUMN amount DECIMAL(20, 2) NULL COMMENT '合计签约金额',
    ADD COLUMN customer_name_snapshot VARCHAR(255) NULL COMMENT '客户名称快照' AFTER customer_id,
    ADD COLUMN customer_mobile_snapshot VARCHAR(32) NULL COMMENT '客户手机号快照' AFTER customer_name_snapshot,
    ADD COLUMN customer_source_snapshot VARCHAR(32) NULL COMMENT '客户创建来源快照' AFTER customer_mobile_snapshot,
    ADD COLUMN owner_name_snapshot VARCHAR(64) NULL COMMENT '合同负责人姓名快照' AFTER owner,
    ADD COLUMN signer_id VARCHAR(32) NULL COMMENT '签约人ID' AFTER owner_name_snapshot,
    ADD COLUMN signer_name_snapshot VARCHAR(64) NULL COMMENT '签约人姓名快照' AFTER signer_id,
    ADD COLUMN signer_dept_id_snapshot VARCHAR(32) NULL COMMENT '签约人部门ID快照' AFTER signer_name_snapshot,
    ADD COLUMN signer_dept_name_snapshot VARCHAR(128) NULL COMMENT '签约人部门名称快照' AFTER signer_dept_id_snapshot,
    ADD COLUMN expected_repayment_amount DECIMAL(20, 2) NOT NULL DEFAULT 0 COMMENT '合计应回款金额' AFTER amount,
    ADD COLUMN effective_version_id VARCHAR(32) NULL COMMENT '当前生效版本ID' AFTER approval_status,
    ADD COLUMN pending_version_id VARCHAR(32) NULL COMMENT '当前待审批版本ID' AFTER effective_version_id,
    ADD COLUMN lock_version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本' AFTER pending_version_id;

ALTER TABLE contract
    ADD UNIQUE KEY uk_contract_org_name (organization_id, name),
    ADD UNIQUE KEY uk_contract_org_number (organization_id, number),
    ADD INDEX idx_contract_org_customer (organization_id, customer_id),
    ADD INDEX idx_contract_org_signer_start (organization_id, signer_id, start_time),
    ADD INDEX idx_contract_org_stage (organization_id, stage),
    ADD INDEX idx_contract_pending_version (pending_version_id),
    ADD INDEX idx_contract_org_eff_start (organization_id, effective_version_id, start_time);

CREATE TABLE contract_version
(
    id                        VARCHAR(32) NOT NULL COMMENT '版本ID',
    contract_id               VARCHAR(32) NOT NULL COMMENT '合同ID',
    version_no                INT         NOT NULL COMMENT '版本号',
    submit_type               VARCHAR(16) NOT NULL COMMENT '提交类型: CREATE/UPDATE',
    approval_status           VARCHAR(32) NOT NULL COMMENT '审批状态',
    base_effective_version_id VARCHAR(32) NULL COMMENT '修改基于的生效版本ID',
    value_snapshot            LONGTEXT    NOT NULL COMMENT '合同核心字段、产品和动态字段快照',
    form_snapshot             LONGTEXT    NULL COMMENT '提交时表单配置快照',
    change_snapshot           LONGTEXT    NULL COMMENT '字段级差异快照',
    approval_dept_id          VARCHAR(32) NULL COMMENT '审批部门ID快照',
    submit_user               VARCHAR(32) NOT NULL COMMENT '提交人',
    submit_time               BIGINT      NOT NULL COMMENT '提交时间',
    approval_user             VARCHAR(32) NULL COMMENT '审批人',
    approval_time             BIGINT      NULL COMMENT '审批时间',
    approval_opinion          VARCHAR(1000) NULL COMMENT '审批意见',
    organization_id           VARCHAR(32) NOT NULL COMMENT '组织ID',
    create_time               BIGINT      NOT NULL COMMENT '创建时间',
    update_time               BIGINT      NOT NULL COMMENT '更新时间',
    create_user               VARCHAR(32) NOT NULL COMMENT '创建人',
    update_user               VARCHAR(32) NOT NULL COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_contract_version_no (contract_id, version_no),
    INDEX idx_contract_version_status (organization_id, approval_status),
    INDEX idx_contract_version_contract (organization_id, contract_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='合同审批版本';

CREATE TABLE contract_product
(
    id                          VARCHAR(32)    NOT NULL COMMENT '产品行ID',
    contract_id                 VARCHAR(32)    NOT NULL COMMENT '合同ID',
    source_version_id           VARCHAR(32)    NOT NULL COMMENT '来源合同版本ID',
    sort_no                     INT            NOT NULL COMMENT '行序号',
    loan_amount                 DECIMAL(20, 2) NOT NULL DEFAULT 0 COMMENT '放款金额',
    point_rate                  DECIMAL(10, 2) NULL COMMENT '点位',
    expected_repayment_amount   DECIMAL(20, 2) NOT NULL DEFAULT 0 COMMENT '应回款金额',
    organization_id             VARCHAR(32)    NOT NULL COMMENT '组织ID',
    create_time                 BIGINT         NOT NULL COMMENT '创建时间',
    update_time                 BIGINT         NOT NULL COMMENT '更新时间',
    create_user                 VARCHAR(32)    NOT NULL COMMENT '创建人',
    update_user                 VARCHAR(32)    NOT NULL COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_contract_product_sort (contract_id, sort_no),
    INDEX idx_contract_product_version (source_version_id),
    INDEX idx_contract_product_report (organization_id, contract_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='合同当前生效产品';

-- 回款主表同样保存当前生效投影。
ALTER TABLE contract_payment_record
    MODIFY COLUMN record_amount DECIMAL(20, 2) NULL COMMENT '合计回款金额',
    ADD COLUMN approval_status VARCHAR(32) NULL COMMENT '审批状态' AFTER payment_plan_id,
    ADD COLUMN effective_version_id VARCHAR(32) NULL COMMENT '当前生效版本ID' AFTER approval_status,
    ADD COLUMN pending_version_id VARCHAR(32) NULL COMMENT '当前待审批版本ID' AFTER effective_version_id,
    ADD COLUMN total_loan_amount DECIMAL(20, 2) NOT NULL DEFAULT 0 COMMENT '合计放款金额' AFTER record_amount,
    ADD COLUMN total_cost_amount DECIMAL(20, 2) NOT NULL DEFAULT 0 COMMENT '合计成本金额' AFTER total_loan_amount,
    ADD COLUMN total_misc_fee_amount DECIMAL(20, 2) NOT NULL DEFAULT 0 COMMENT '合计杂费金额' AFTER total_cost_amount,
    ADD COLUMN total_commission_amount DECIMAL(20, 2) NOT NULL DEFAULT 0 COMMENT '合计返佣金额' AFTER total_misc_fee_amount,
    ADD COLUMN total_revenue_amount DECIMAL(20, 2) NOT NULL DEFAULT 0 COMMENT '合计创收金额' AFTER total_commission_amount,
    ADD COLUMN first_loan_time BIGINT NULL COMMENT '最早放款时间' AFTER record_end_time,
    ADD COLUMN last_loan_time BIGINT NULL COMMENT '最晚放款时间' AFTER first_loan_time,
    ADD COLUMN first_repayment_time BIGINT NULL COMMENT '最早回款时间' AFTER last_loan_time,
    ADD COLUMN last_repayment_time BIGINT NULL COMMENT '最晚回款时间' AFTER first_repayment_time,
    ADD COLUMN lock_version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本' AFTER last_repayment_time;

ALTER TABLE contract_payment_record
    ADD UNIQUE KEY uk_payment_record_org_name (organization_id, name),
    ADD UNIQUE KEY uk_payment_record_org_no (organization_id, no),
    ADD INDEX idx_payment_record_pending_version (pending_version_id),
    ADD INDEX idx_payment_record_report (organization_id, contract_id, approval_status);

CREATE TABLE contract_payment_record_version
(
    id                        VARCHAR(32) NOT NULL COMMENT '版本ID',
    payment_record_id         VARCHAR(32) NOT NULL COMMENT '回款记录ID',
    contract_id               VARCHAR(32) NOT NULL COMMENT '合同ID',
    version_no                INT         NOT NULL COMMENT '版本号',
    submit_type               VARCHAR(16) NOT NULL COMMENT '提交类型: CREATE/UPDATE',
    approval_status           VARCHAR(32) NOT NULL COMMENT '审批状态',
    base_effective_version_id VARCHAR(32) NULL COMMENT '修改基于的生效版本ID',
    value_snapshot            LONGTEXT    NOT NULL COMMENT '回款核心字段、产品和动态字段快照',
    form_snapshot             LONGTEXT    NULL COMMENT '提交时表单配置快照',
    change_snapshot           LONGTEXT    NULL COMMENT '字段级差异快照',
    signer_id_snapshot        VARCHAR(32) NULL COMMENT '合同签约人ID快照',
    signer_name_snapshot      VARCHAR(64) NULL COMMENT '合同签约人姓名快照',
    approval_dept_id          VARCHAR(32) NULL COMMENT '审批部门ID快照',
    submit_user               VARCHAR(32) NOT NULL COMMENT '提交人',
    submit_time               BIGINT      NOT NULL COMMENT '提交时间',
    approval_user             VARCHAR(32) NULL COMMENT '审批人',
    approval_time             BIGINT      NULL COMMENT '审批时间',
    approval_opinion          VARCHAR(1000) NULL COMMENT '审批意见',
    organization_id           VARCHAR(32) NOT NULL COMMENT '组织ID',
    create_time               BIGINT      NOT NULL COMMENT '创建时间',
    update_time               BIGINT      NOT NULL COMMENT '更新时间',
    create_user               VARCHAR(32) NOT NULL COMMENT '创建人',
    update_user               VARCHAR(32) NOT NULL COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_record_version_no (payment_record_id, version_no),
    INDEX idx_payment_record_version_status (organization_id, approval_status),
    INDEX idx_payment_record_version_contract (organization_id, contract_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='回款记录审批版本';

CREATE TABLE contract_payment_record_product
(
    id                         VARCHAR(32)    NOT NULL COMMENT '产品行ID',
    payment_record_id          VARCHAR(32)    NOT NULL COMMENT '回款记录ID',
    contract_id                VARCHAR(32)    NOT NULL COMMENT '合同ID',
    signer_id                  VARCHAR(32)    NULL COMMENT '签约人ID快照',
    signer_name_snapshot       VARCHAR(128)   NULL COMMENT '签约人姓名快照',
    signer_dept_id_snapshot    VARCHAR(32)    NULL COMMENT '签约人部门ID快照',
    signer_dept_name_snapshot  VARCHAR(128)   NULL COMMENT '签约人部门名快照',
    customer_source_snapshot   VARCHAR(64)    NULL COMMENT '客户来源快照',
    source_version_id          VARCHAR(32)    NOT NULL COMMENT '来源回款版本ID',
    sort_no                    INT            NOT NULL COMMENT '行序号',
    loan_time                  BIGINT         NULL COMMENT '放款时间',
    loan_amount                DECIMAL(20, 2) NOT NULL DEFAULT 0 COMMENT '放款金额',
    repayment_time             BIGINT         NULL COMMENT '回款时间',
    repayment_amount           DECIMAL(20, 2) NOT NULL DEFAULT 0 COMMENT '回款金额',
    cost_amount                DECIMAL(20, 2) NOT NULL DEFAULT 0 COMMENT '成本金额',
    misc_fee_amount            DECIMAL(20, 2) NOT NULL DEFAULT 0 COMMENT '杂费金额',
    commission_amount          DECIMAL(20, 2) NOT NULL DEFAULT 0 COMMENT '返佣金额',
    revenue_formula            VARCHAR(1000)  NOT NULL DEFAULT '0' COMMENT '创收公式原文',
    revenue_formula_normalized VARCHAR(1000)  NOT NULL DEFAULT '0' COMMENT '标准化创收公式',
    revenue_amount             DECIMAL(20, 2) NOT NULL DEFAULT 0 COMMENT '创收金额',
    organization_id            VARCHAR(32)    NOT NULL COMMENT '组织ID',
    create_time                BIGINT         NOT NULL COMMENT '创建时间',
    update_time                BIGINT         NOT NULL COMMENT '更新时间',
    create_user                VARCHAR(32)    NOT NULL COMMENT '创建人',
    update_user                VARCHAR(32)    NOT NULL COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_record_product_sort (payment_record_id, sort_no),
    INDEX idx_payment_product_version (source_version_id),
    INDEX idx_payment_product_loan_report (organization_id, loan_time, contract_id),
    INDEX idx_payment_product_repayment_report (organization_id, repayment_time, contract_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='回款记录当前生效产品';

CREATE TABLE report_customer_conversion_event
(
    id                 VARCHAR(32)  NOT NULL COMMENT '事件ID',
    event_type         VARCHAR(32)  NOT NULL COMMENT 'CONTRACT_SIGNED/PAYMENT_APPROVED',
    business_id        VARCHAR(32)  NOT NULL COMMENT '合同或回款业务ID',
    contract_id        VARCHAR(32)  NOT NULL COMMENT '合同ID',
    payment_record_id  VARCHAR(32)  NULL COMMENT '回款记录ID',
    customer_id        VARCHAR(32)  NOT NULL COMMENT '客户ID',
    customer_name      VARCHAR(255) NOT NULL COMMENT '客户名称快照',
    customer_mobile    VARCHAR(32)  NULL COMMENT '客户手机号快照',
    customer_source    VARCHAR(32)  NULL COMMENT '客户创建来源快照',
    signer_id          VARCHAR(32)  NOT NULL COMMENT '签约人ID快照',
    signer_name        VARCHAR(64)  NOT NULL COMMENT '签约人姓名快照',
    signer_dept_id     VARCHAR(32)  NULL COMMENT '签约人部门ID快照',
    signer_dept_name   VARCHAR(128) NULL COMMENT '签约人部门名称快照',
    event_time         BIGINT       NOT NULL COMMENT '业务事件时间',
    stat_date          DATE         NOT NULL COMMENT '业务事件日期',
    organization_id    VARCHAR(32)  NOT NULL COMMENT '组织ID',
    create_time        BIGINT       NOT NULL COMMENT '创建时间',
    update_time        BIGINT       NOT NULL COMMENT '更新时间',
    create_user        VARCHAR(32)  NOT NULL COMMENT '创建人',
    update_user        VARCHAR(32)  NOT NULL COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_customer_conversion_business (organization_id, event_type, business_id),
    INDEX idx_customer_conversion_summary (organization_id, stat_date, signer_id, event_type),
    INDEX idx_customer_conversion_dept (organization_id, stat_date, signer_dept_id),
    INDEX idx_customer_conversion_customer (organization_id, customer_id),
    INDEX idx_conversion_event_time (organization_id, event_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户转化历史事件';

-- 员工统计事件表按组织、指标、事件时间和有效标志查询。
ALTER TABLE report_employee_stat_event
    ADD INDEX idx_stat_event_report (organization_id, metric_type, event_time, valid_flag);
