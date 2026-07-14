package cn.cordys.mmba.domain;

import cn.cordys.common.domain.BaseModel;
import jakarta.persistence.Table;
import lombok.Data;

/** 每个租户一份的定时清除通话记录配置。 */
@Data
@Table(name = "mmba_call_log_clean_config")
public class MmbaCallLogCleanConfig extends BaseModel {
    private String organizationId;
    private Boolean enable;
}
