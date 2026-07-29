package cn.cordys.crm.report.customerrecording.dto.response;

import lombok.Data;

@Data
public class CustomerRecordingListResponse {

    private String auditId;

    private String operatorUserId;

    private String employeeName;

    private String departmentId;

    private String departmentName;

    private String customerId;

    private String customerName;

    private String customerTel;

    private String beginTime;

    private String answerTime;

    private String endTime;

    private Integer duration;
}
