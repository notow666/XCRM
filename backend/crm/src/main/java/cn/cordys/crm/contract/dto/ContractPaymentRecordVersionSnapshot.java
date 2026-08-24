package cn.cordys.crm.contract.dto;

import cn.cordys.common.domain.BaseModuleFieldValue;
import cn.cordys.crm.contract.dto.request.ContractPaymentRecordProductRequest;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class ContractPaymentRecordVersionSnapshot {

    private Integer schemaVersion = 2;
    private String name;
    private String no;
    private String contractId;
    private String owner;
    private BigDecimal recordAmount;
    private BigDecimal totalLoanAmount;
    private BigDecimal totalCostAmount;
    private BigDecimal totalMiscFeeAmount;
    private BigDecimal totalCommissionAmount;
    private BigDecimal totalRevenueAmount;
    private Long firstLoanTime;
    private Long lastLoanTime;
    private Long firstRepaymentTime;
    private Long lastRepaymentTime;
    private List<ContractPaymentRecordProductRequest> products = new ArrayList<>();
    private List<BaseModuleFieldValue> moduleFields = new ArrayList<>();
}
