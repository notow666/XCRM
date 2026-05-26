alter table report_employee_stat_event
    add column customer_source varchar(255) null comment '客户来源快照' after customer_mobile;
