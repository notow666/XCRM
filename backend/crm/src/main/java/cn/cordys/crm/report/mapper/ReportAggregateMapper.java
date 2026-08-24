package cn.cordys.crm.report.mapper;

import cn.cordys.mybatis.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

/**
 * 报表聚合查询（SQL 层 GROUP BY，避免全量加载后 Java 聚合导致大数据量下接口慢）。
 */
public interface ReportAggregateMapper {

    /**
     * 客户转化-上门客户数（按维度分组去重客户数）。
     */
    List<ConversionAggregateRow> countVisitCustomers(@Param("orgId") String orgId,
                                                     @Param("startTime") long startTime,
                                                     @Param("endTime") long endTime,
                                                     @Param("dimensionType") String dimensionType,
                                                     @Param("visibleEmployees") Set<String> visibleEmployees);

    /**
     * 客户转化-签约/回款客户数（按维度分组去重客户数）。
     */
    List<ConversionAggregateRow> countConversionCustomers(@Param("orgId") String orgId,
                                                          @Param("eventType") String eventType,
                                                          @Param("startTime") long startTime,
                                                          @Param("endTime") long endTime,
                                                          @Param("dimensionType") String dimensionType,
                                                          @Param("visibleEmployees") Set<String> visibleEmployees);

    /**
     * 合同成交分析-合同签约数/金额（按维度分组）。
     */
    List<ContractAggregateRow> aggregateContracts(@Param("orgId") String orgId,
                                                  @Param("startTime") long startTime,
                                                  @Param("endTime") long endTime,
                                                  @Param("dimensionType") String dimensionType,
                                                  @Param("visibleEmployees") Set<String> visibleEmployees);

    /**
     * 合同成交分析-放款/回款/创收（按维度分组，join 合同取签约人；放款按 loan_time）。
     */
    List<ContractAggregateRow> aggregateProducts(@Param("orgId") String orgId,
                                                 @Param("startTime") long startTime,
                                                 @Param("endTime") long endTime,
                                                 @Param("dimensionType") String dimensionType,
                                                 @Param("visibleEmployees") Set<String> visibleEmployees);

    /**
     * 合同成交分析-回款/创收（按维度分组，join 合同取签约人；按 repayment_time）。
     */
    List<ContractAggregateRow> aggregateProductsRepayment(@Param("orgId") String orgId,
                                                          @Param("startTime") long startTime,
                                                          @Param("endTime") long endTime,
                                                          @Param("dimensionType") String dimensionType,
                                                          @Param("visibleEmployees") Set<String> visibleEmployees);

    /**
     * 客户转化-涉及员工（distinct，供可见性过滤）。
     */
    List<String> distinctConversionEmployees(@Param("orgId") String orgId, @Param("eventType") String eventType,
                                             @Param("startTime") long startTime, @Param("endTime") long endTime);

    /**
     * 客户转化-上门涉及员工（distinct，供可见性过滤）。
     */
    List<String> distinctVisitEmployees(@Param("orgId") String orgId, @Param("startTime") long startTime,
                                        @Param("endTime") long endTime);

    /**
     * 合同成交分析-涉及签约人（distinct，供可见性过滤）。
     */
    List<String> distinctContractSigners(@Param("orgId") String orgId, @Param("startTime") long startTime,
                                         @Param("endTime") long endTime);

    /**
     * 客户转化明细（SQL 分页）。
     */
    List<ConversionDetailRow> listConversionDetails(@Param("orgId") String orgId, @Param("eventType") String eventType,
                                                    @Param("startTime") long startTime, @Param("endTime") long endTime,
                                                    @Param("visibleEmployees") Set<String> visibleEmployees,
                                                    @Param("dimensionType") String dimensionType,
                                                    @Param("dimensionKey") String dimensionKey,
                                                    @Param("offset") int offset, @Param("pageSize") int pageSize);

    long countConversionDetails(@Param("orgId") String orgId, @Param("eventType") String eventType,
                                @Param("startTime") long startTime, @Param("endTime") long endTime,
                                @Param("visibleEmployees") Set<String> visibleEmployees,
                                @Param("dimensionType") String dimensionType,
                                @Param("dimensionKey") String dimensionKey);

    /**
     * 上门明细（SQL 分页）。
     */
    List<ConversionDetailRow> listVisitDetails(@Param("orgId") String orgId,
                                               @Param("startTime") long startTime, @Param("endTime") long endTime,
                                               @Param("visibleEmployees") Set<String> visibleEmployees,
                                               @Param("dimensionType") String dimensionType,
                                               @Param("dimensionKey") String dimensionKey,
                                               @Param("offset") int offset, @Param("pageSize") int pageSize);

    long countVisitDetails(@Param("orgId") String orgId, @Param("startTime") long startTime,
                           @Param("endTime") long endTime, @Param("visibleEmployees") Set<String> visibleEmployees,
                           @Param("dimensionType") String dimensionType,
                           @Param("dimensionKey") String dimensionKey);

    /**
     * 合同成交分析-合同签约明细（SQL 分页）。
     */
    List<ContractDetailRow> listContractDetails(@Param("orgId") String orgId,
                                                @Param("startTime") long startTime, @Param("endTime") long endTime,
                                                @Param("visibleEmployees") Set<String> visibleEmployees,
                                                @Param("dimensionType") String dimensionType,
                                                @Param("dimensionKey") String dimensionKey,
                                                @Param("offset") int offset, @Param("pageSize") int pageSize);

    long countContractDetails(@Param("orgId") String orgId, @Param("startTime") long startTime,
                              @Param("endTime") long endTime, @Param("visibleEmployees") Set<String> visibleEmployees,
                              @Param("dimensionType") String dimensionType,
                              @Param("dimensionKey") String dimensionKey);

    /**
     * 合同成交分析-放款明细（SQL 分页，按 loan_time）。
     */
    List<ContractDetailRow> listProductDetails(@Param("orgId") String orgId,
                                               @Param("startTime") long startTime, @Param("endTime") long endTime,
                                               @Param("visibleEmployees") Set<String> visibleEmployees,
                                               @Param("dimensionType") String dimensionType,
                                               @Param("dimensionKey") String dimensionKey,
                                               @Param("offset") int offset, @Param("pageSize") int pageSize);

    long countProductDetails(@Param("orgId") String orgId, @Param("startTime") long startTime,
                             @Param("endTime") long endTime, @Param("visibleEmployees") Set<String> visibleEmployees,
                             @Param("dimensionType") String dimensionType,
                             @Param("dimensionKey") String dimensionKey);

    /**
     * 合同成交分析-回款/创收明细（SQL 分页，按 repayment_time）。
     */
    List<ContractDetailRow> listProductDetailsByRepayment(@Param("orgId") String orgId,
                                                          @Param("startTime") long startTime, @Param("endTime") long endTime,
                                                          @Param("visibleEmployees") Set<String> visibleEmployees,
                                                          @Param("dimensionType") String dimensionType,
                                                          @Param("dimensionKey") String dimensionKey,
                                                          @Param("offset") int offset, @Param("pageSize") int pageSize);

    long countProductDetailsByRepayment(@Param("orgId") String orgId, @Param("startTime") long startTime,
                                        @Param("endTime") long endTime, @Param("visibleEmployees") Set<String> visibleEmployees,
                                        @Param("dimensionType") String dimensionType,
                                        @Param("dimensionKey") String dimensionKey);

    class ConversionAggregateRow {
        private String dimKey;
        private String dimLabel;
        private long customerCount;

        public String getDimKey() { return dimKey; }
        public void setDimKey(String dimKey) { this.dimKey = dimKey; }
        public String getDimLabel() { return dimLabel; }
        public void setDimLabel(String dimLabel) { this.dimLabel = dimLabel; }
        public long getCustomerCount() { return customerCount; }
        public void setCustomerCount(long customerCount) { this.customerCount = customerCount; }
    }

    class ContractAggregateRow {
        private String dimKey;
        private String dimLabel;
        private long contractCount;
        private long contractAmount;
        private long loanAmount;
        private long repaymentAmount;
        private long revenueAmount;

        public String getDimKey() { return dimKey; }
        public void setDimKey(String dimKey) { this.dimKey = dimKey; }
        public String getDimLabel() { return dimLabel; }
        public void setDimLabel(String dimLabel) { this.dimLabel = dimLabel; }
        public long getContractCount() { return contractCount; }
        public void setContractCount(long contractCount) { this.contractCount = contractCount; }
        public long getContractAmount() { return contractAmount; }
        public void setContractAmount(long contractAmount) { this.contractAmount = contractAmount; }
        public long getLoanAmount() { return loanAmount; }
        public void setLoanAmount(long loanAmount) { this.loanAmount = loanAmount; }
        public long getRepaymentAmount() { return repaymentAmount; }
        public void setRepaymentAmount(long repaymentAmount) { this.repaymentAmount = repaymentAmount; }
        public long getRevenueAmount() { return revenueAmount; }
        public void setRevenueAmount(long revenueAmount) { this.revenueAmount = revenueAmount; }
    }

    class ConversionDetailRow {
        private String eventType;
        private String businessId;
        private String customerId;
        private String customerName;
        private String customerMobile;
        private String employeeId;
        private String employeeName;
        private String departmentId;
        private String departmentName;
        private long eventTime;
        private java.time.LocalDate statDate;

        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public String getBusinessId() { return businessId; }
        public void setBusinessId(String businessId) { this.businessId = businessId; }
        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public String getCustomerName() { return customerName; }
        public void setCustomerName(String customerName) { this.customerName = customerName; }
        public String getCustomerMobile() { return customerMobile; }
        public void setCustomerMobile(String customerMobile) { this.customerMobile = customerMobile; }
        public String getEmployeeId() { return employeeId; }
        public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
        public String getEmployeeName() { return employeeName; }
        public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
        public String getDepartmentId() { return departmentId; }
        public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
        public String getDepartmentName() { return departmentName; }
        public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
        public long getEventTime() { return eventTime; }
        public void setEventTime(long eventTime) { this.eventTime = eventTime; }
        public java.time.LocalDate getStatDate() { return statDate; }
        public void setStatDate(java.time.LocalDate statDate) { this.statDate = statDate; }
    }

    class ContractDetailRow {
        private String metricType;
        private String resourceId;
        private String paymentRecordId;
        private String contractId;
        private String contractName;
        private String customerId;
        private String customerName;
        private String customerMobile;
        private String customerSource;
        private String signerId;
        private String signerName;
        private String departmentId;
        private String departmentName;
        private long businessTime;
        private long amount;
        private long revenueAmount;

        public String getMetricType() { return metricType; }
        public void setMetricType(String metricType) { this.metricType = metricType; }
        public String getResourceId() { return resourceId; }
        public void setResourceId(String resourceId) { this.resourceId = resourceId; }
        public String getPaymentRecordId() { return paymentRecordId; }
        public void setPaymentRecordId(String paymentRecordId) { this.paymentRecordId = paymentRecordId; }
        public String getContractId() { return contractId; }
        public void setContractId(String contractId) { this.contractId = contractId; }
        public String getContractName() { return contractName; }
        public void setContractName(String contractName) { this.contractName = contractName; }
        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public String getCustomerName() { return customerName; }
        public void setCustomerName(String customerName) { this.customerName = customerName; }
        public String getCustomerMobile() { return customerMobile; }
        public void setCustomerMobile(String customerMobile) { this.customerMobile = customerMobile; }
        public String getCustomerSource() { return customerSource; }
        public void setCustomerSource(String customerSource) { this.customerSource = customerSource; }
        public String getSignerId() { return signerId; }
        public void setSignerId(String signerId) { this.signerId = signerId; }
        public String getSignerName() { return signerName; }
        public void setSignerName(String signerName) { this.signerName = signerName; }
        public String getDepartmentId() { return departmentId; }
        public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
        public String getDepartmentName() { return departmentName; }
        public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
        public long getBusinessTime() { return businessTime; }
        public void setBusinessTime(long businessTime) { this.businessTime = businessTime; }
        public long getAmount() { return amount; }
        public void setAmount(long amount) { this.amount = amount; }
        public long getRevenueAmount() { return revenueAmount; }
        public void setRevenueAmount(long revenueAmount) { this.revenueAmount = revenueAmount; }
    }
}
