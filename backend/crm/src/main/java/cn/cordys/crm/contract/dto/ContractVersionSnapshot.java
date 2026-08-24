package cn.cordys.crm.contract.dto;

import cn.cordys.common.domain.BaseModuleFieldValue;
import cn.cordys.crm.contract.dto.request.ContractProductRequest;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class ContractVersionSnapshot {

    private Integer schemaVersion = 2;
    private String name;
    private String number;
    private String customerId;
    private String customerNameSnapshot;
    private String customerMobileSnapshot;
    private String customerSourceSnapshot;
    private String owner;
    private String ownerNameSnapshot;
    private String signerId;
    private String signerNameSnapshot;
    private String signerDeptIdSnapshot;
    private String signerDeptNameSnapshot;
    private BigDecimal amount;
    private BigDecimal expectedRepaymentAmount;
    private Long startTime;
    private Long endTime;
    private List<ContractProductRequest> products = new ArrayList<>();
    private List<BaseModuleFieldValue> moduleFields = new ArrayList<>();
}
