package cn.cordys.crm.customer.domain;

import jakarta.persistence.Table;
import lombok.Data;

@Data
@Table(name = "sys_job_cron_detail")
public class SysJobCronDetail {

    private Integer id;

    private String methodName;

    private String description;

    private String cron;

    private Boolean enable;

    private java.time.LocalDateTime createTime;

    private java.time.LocalDateTime updateTime;
}
