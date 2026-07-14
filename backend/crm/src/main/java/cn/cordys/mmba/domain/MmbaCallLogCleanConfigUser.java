package cn.cordys.mmba.domain;

import cn.cordys.common.domain.BaseModel;
import jakarta.persistence.Table;
import lombok.Data;

/** 定时清除配置与员工的关系。关闭配置时仍保留这些关系。 */
@Data
@Table(name = "mmba_call_log_clean_config_user")
public class MmbaCallLogCleanConfigUser extends BaseModel {
    private String configId;
    private String userId;
}
