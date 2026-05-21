package cn.cordys.platform.dto;

import lombok.Data;

@Data
public class PlatformUserAdminItemResponse {

    private String id;
    private String username;
    private String nickname;
    private String status;
    private Long createTime;
    private Long updateTime;
}
