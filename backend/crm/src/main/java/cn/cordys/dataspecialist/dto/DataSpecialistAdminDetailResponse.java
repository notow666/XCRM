package cn.cordys.dataspecialist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
public class DataSpecialistAdminDetailResponse {

    private String id;
    private String username;
    private String name;
    private String remark;
    private Boolean enabled;
    private Long createTime;
    private Long updateTime;

    @Schema(description = "可导入租户ID列表")
    private List<String> tenantIds;
}
