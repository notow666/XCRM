ALTER TABLE platform_user
    ADD COLUMN nickname VARCHAR(128) NULL COMMENT '昵称' AFTER username;
