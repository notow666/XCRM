CREATE TABLE IF NOT EXISTS platform_phone_segment (
    id              VARCHAR(32)  NOT NULL COMMENT '主键',
    province        VARCHAR(64)  NOT NULL COMMENT '省份(B列)',
    city            VARCHAR(64)  NOT NULL COMMENT '城市(C列)',
    segment         VARCHAR(20)  NOT NULL COMMENT '号段(A列)',
    prefix          VARCHAR(3)   NOT NULL COMMENT '号段前三位',
    isp             VARCHAR(64)  NULL COMMENT '运营商(D列)',
    area_code       VARCHAR(16)  NULL COMMENT '地区编码(对应region.json code,4位)',
    PRIMARY KEY (id),
    KEY idx_phone_segment_province_city (province, city),
    KEY idx_phone_segment (segment),
    KEY idx_phone_segment_prefix (prefix),
    KEY idx_phone_segment_province_city_prefix (province, city, prefix)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='号段管理(只读)';

