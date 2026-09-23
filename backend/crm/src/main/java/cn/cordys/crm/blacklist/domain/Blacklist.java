package cn.cordys.crm.blacklist.domain;

import lombok.Data;

@Data
public class Blacklist {
    private String id;
    private String mobile;
    private String customerName;
    private Long createTime;
    private String createUser;
    private Long updateTime;
    private String updateUser;
}
