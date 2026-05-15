package cn.cordys.dataspecialist.dto;

import lombok.Data;

@Data
public class DataSpecialistAdminItemResponse {

    private String id;
    private String username;
    private String name;
    private String remark;
    private Boolean enabled;
    private Long createTime;
    private Long updateTime;
}
