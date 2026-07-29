ALTER TABLE sys_user_extend
    ADD COLUMN default_call_card_slot_num TINYINT DEFAULT NULL
        COMMENT '默认拨号卡槽：1-卡槽1，2-卡槽2';
