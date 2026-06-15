alter table report_employee_stat_day
    add column visit_customer_count int not null default 0 comment '上门客户数' after new_wechat_friend_count;
