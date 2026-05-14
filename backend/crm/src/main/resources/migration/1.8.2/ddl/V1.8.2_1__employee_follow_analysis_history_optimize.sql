ALTER TABLE report_employee_follow_analysis_fact_day
    ADD INDEX idx_report_emp_follow_fact_day_history_cover
    (
        stat_date,
        operator_user_id,
        customer_id,
        inbound_customer_flag,
        contacted_customer_flag,
        new_wechat_friend_flag,
        dial_count,
        connected_count,
        call_over_1min_count,
        call_over_3min_count,
        call_duration_sec
    );
