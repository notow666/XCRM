package cn.cordys.crm.customer.dto.response;

import lombok.Data;

@Data
public class CustomerNextStageResponse {
    private String customerId;
    private String customerName;
    private String currentStageId;
    private String currentStageName;
    private String nextStageId;
    private String nextStageName;
    private String owner;
    private String ownerName;
}
