alter table report_employee_stat_event
    add column valid_flag tinyint(1) not null default 1 comment '有效标识 0待确认 1已确认' after source_table_name;
