ALTER TABLE data_specialist
    ADD COLUMN specialist_name VARCHAR(128) NULL COMMENT '名称' AFTER username,
    ADD COLUMN remark VARCHAR(512) NULL COMMENT '备注' AFTER specialist_name;
