package cn.cordys.crm.customer.dto.response;

import lombok.Data;

@Data
public class CustomerCallRecordListResponse {
    private String id;
    private String customerId;
    private String employeeName;
    private String beginTime;
    private String endTime;
    private Integer isConnected;
    private Integer duration;
    private String mediaFileId;
    private String mediaFileName;
}
