package cn.cordys.crm.customer.mapper;

import cn.cordys.common.dto.BasePageRequest;
import cn.cordys.common.dto.BatchUpdateDbParam;
import cn.cordys.common.dto.DeptDataPermissionDTO;
import cn.cordys.common.dto.OptionDTO;
import cn.cordys.common.dto.chart.ChartResult;
import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.crm.customer.dto.MobileConflictDTO;
import cn.cordys.crm.customer.dto.request.CustomerBatchTransferRequest;
import cn.cordys.crm.customer.dto.request.CustomerChartAnalysisDbRequest;
import cn.cordys.crm.customer.dto.request.CustomerPageRequest;
import cn.cordys.crm.customer.dto.response.CustomerListResponse;
import cn.cordys.crm.home.dto.request.HomeStatisticSearchWrapperRequest;
import cn.cordys.crm.search.response.advanced.AdvancedCustomerPoolResponse;
import cn.cordys.crm.search.response.advanced.AdvancedCustomerResponse;
import cn.cordys.crm.search.response.global.GlobalCustomerPoolResponse;
import cn.cordys.crm.search.response.global.GlobalCustomerResponse;
import cn.cordys.crm.system.dto.FilterConditionDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author jianxing
 * @date 2025-02-08 17:42:41
 */
public interface ExtCustomerMapper {

    List<CustomerListResponse> list(@Param("request") CustomerPageRequest request, @Param("orgId") String orgId,
                                    @Param("userId") String userId, @Param("dataPermission") DeptDataPermissionDTO deptDataPermission);

    List<CustomerListResponse> sourceList(@Param("request") CustomerPageRequest request, @Param("orgId") String orgId,
                                          @Param("userId") String userId, @Param("dataPermission") DeptDataPermissionDTO deptDataPermission);

    List<OptionDTO> selectOptionByIds(@Param("ids") List<String> ids);

    void batchTransfer(@Param("request") CustomerBatchTransferRequest request, @Param("userId") String userId);

    List<AdvancedCustomerResponse> checkRepeatCustomer(@Param("request") CustomerPageRequest request, @Param("orgId") String orgId,
                                                       @Param("userId") String userId);

    int countByOwner(@Param("owner") String owner);

    /**
     * 移入公海
     *
     * @param customer 客户
     */
    void moveToPool(@Param("customer") Customer customer);

    List<OptionDTO> getCustomerOptions(@Param("keyword") String keyword, @Param("orgId") String orgId);

    List<OptionDTO> getCustomerOptionsByIds(@Param("ids") List<String> ids);

    boolean hasRefOpportunity(@Param("ids") List<String> ids);

    boolean hasRefContact(@Param("ids") List<String> ids);

    /**
     * 查询负责人过滤条件下的客户数量
     *
     * @param ownerId 负责人ID
     * @param filters 过滤条件集合
     *
     * @return 客户数量
     */
    long filterOwnerCount(@Param("ownerId") String ownerId, @Param("filters") List<FilterConditionDTO> filters);

    List<CustomerListResponse> getListByIds(@Param("ids") List<String> ids);

    Long selectCustomerCount(@Param("request") HomeStatisticSearchWrapperRequest request, @Param("unfollowed") boolean unfollowed);

    List<AdvancedCustomerPoolResponse> customerPoolList(@Param("request") BasePageRequest request, @Param("orgId") String orgId);

    List<GlobalCustomerPoolResponse> globalPoolSearchList(@Param("request") BasePageRequest request, @Param("orgId") String orgId);

    long globalPoolSearchListCount(@Param("request") BasePageRequest request, @Param("orgId") String orgId);


    List<GlobalCustomerResponse> globalSearchList(@Param("request") BasePageRequest request, @Param("orgId") String orgId);

    long globalSearchListCount(@Param("request") BasePageRequest request, @Param("orgId") String orgId);

    /**
     * 根据ID全量更新客户信息
     *
     * @param customer 客户
     */
    void updateIncludeNullById(@Param("customer") Customer customer);

    void moveToPoolIncludeStage(@Param("customer") Customer customer);

    void batchMoveToPoolIncludeStage(@Param("customers") List<Customer> customers);

    void batchUpdate(@Param("request") BatchUpdateDbParam request);

    List<OptionDTO> getCustomerPoolId(@Param("ids") List<String> ids);

    List<ChartResult> chart(@Param("request") CustomerChartAnalysisDbRequest request, @Param("userId") String userId, @Param("orgId") String orgId,
                            @Param("dataPermission") DeptDataPermissionDTO dataPermission);

    /**
     * 根据阶段ID统计客户数量
     *
     * @param stageId 阶段ID
     * @param orgId   组织ID
     * @return 客户数量
     */
    int countByStage(@Param("stageId") String stageId, @Param("orgId") String orgId);

    /**
     * 统计指定用户下属于特定阶段的客户数量（用于库容计算排除）
     *
     * @param ownerId     用户ID
     * @param stageIds    阶段ID列表
     * @return 客户数量
     */
    int countByOwnerAndStages(@Param("ownerId") String ownerId, @Param("stageIds") List<String> stageIds);

    /**
     * 合并查询手机号冲突信息（客户池冲突 + 其他公海池冲突）
     * 替代原来的 getPrivatePoolMobiles 和 getOtherPoolMobiles 两次查询
     *
     * @param orgId   组织ID
     * @param poolId  目标公海池ID
     * @param mobiles 手机号列表
     * @return 冲突信息列表，包含手机号和冲突类型
     */
    List<MobileConflictDTO> getMobileConflicts(@Param("orgId") String orgId, @Param("poolId") String poolId, @Param("mobiles") List<String> mobiles);

    List<Customer> getPoolCustomersByMobiles(@Param("orgId") String orgId, @Param("poolId") String poolId, @Param("mobiles") List<String> mobiles);

}
