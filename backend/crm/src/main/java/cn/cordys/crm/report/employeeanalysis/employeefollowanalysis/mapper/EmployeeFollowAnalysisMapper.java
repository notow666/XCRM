package cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.mapper;

import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.request.EmployeeFollowAnalysisDrilldownRequest;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response.EmployeeFollowAnalysisCustomerContextRow;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response.EmployeeFollowAnalysisDepartmentDimensionRow;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response.EmployeeFollowAnalysisDrilldownItemResponse;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response.EmployeeFollowAnalysisEmployeeDimensionRow;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response.EmployeeFollowAnalysisHistoryAggregateRow;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response.EmployeeFollowAnalysisMetricRow;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface EmployeeFollowAnalysisMapper {

    List<EmployeeFollowAnalysisMetricRow> listFactRows(@Param("startDate") String startDate,
                                                       @Param("endDate") String endDate);

    List<EmployeeFollowAnalysisHistoryAggregateRow> listHistoryRowsByEmployee(@Param("startDate") String startDate,
                                                                              @Param("endDate") String endDate,
                                                                              @Param("operatorUserIds") List<String> operatorUserIds);

    List<EmployeeFollowAnalysisHistoryAggregateRow> listHistoryRowsByDepartment(@Param("startDate") String startDate,
                                                                                @Param("endDate") String endDate,
                                                                                @Param("orgId") String orgId,
                                                                                @Param("operatorUserIds") List<String> operatorUserIds);

    List<EmployeeFollowAnalysisHistoryAggregateRow> listHistoryRowsByCustomerSource(@Param("startDate") String startDate,
                                                                                    @Param("endDate") String endDate,
                                                                                    @Param("operatorUserIds") List<String> operatorUserIds);

    List<EmployeeFollowAnalysisHistoryAggregateRow> listHistoryRowsByStatDay(@Param("startDate") String startDate,
                                                                             @Param("endDate") String endDate,
                                                                             @Param("operatorUserIds") List<String> operatorUserIds);

    List<EmployeeFollowAnalysisHistoryAggregateRow> listHistoryRowsByStatMonth(@Param("startDate") String startDate,
                                                                               @Param("endDate") String endDate,
                                                                               @Param("operatorUserIds") List<String> operatorUserIds);

    List<EmployeeFollowAnalysisMetricRow> listInboundRows(@Param("startTime") Long startTime,
                                                          @Param("endTime") Long endTime,
                                                          @Param("orgId") String orgId,
                                                          @Param("operatorUserIds") List<String> operatorUserIds);

    List<EmployeeFollowAnalysisMetricRow> listContactedRows(@Param("startTime") Long startTime,
                                                            @Param("endTime") Long endTime,
                                                            @Param("orgId") String orgId,
                                                            @Param("operatorUserIds") List<String> operatorUserIds);

    List<EmployeeFollowAnalysisMetricRow> listCallRows(@Param("startTime") Long startTime,
                                                       @Param("endTime") Long endTime,
                                                       @Param("orgId") String orgId,
                                                       @Param("operatorUserIds") List<String> operatorUserIds);

    List<EmployeeFollowAnalysisMetricRow> listWechatRows(@Param("startTime") Long startTime,
                                                         @Param("endTime") Long endTime,
                                                         @Param("orgId") String orgId,
                                                         @Param("operatorUserIds") List<String> operatorUserIds);

    List<EmployeeFollowAnalysisEmployeeDimensionRow> listCurrentEmployees(@Param("orgId") String orgId);

    List<EmployeeFollowAnalysisDepartmentDimensionRow> listCurrentDepartments(@Param("orgId") String orgId);

    List<EmployeeFollowAnalysisCustomerContextRow> listCustomerContexts(@Param("orgId") String orgId);

    List<EmployeeFollowAnalysisDrilldownItemResponse> listInboundCustomerDrilldown(@Param("request") EmployeeFollowAnalysisDrilldownRequest request,
                                                                                    @Param("orgId") String orgId,
                                                                                    @Param("operatorUserIds") List<String> operatorUserIds);

    List<EmployeeFollowAnalysisDrilldownItemResponse> listContactedCustomerDrilldown(@Param("request") EmployeeFollowAnalysisDrilldownRequest request,
                                                                                      @Param("orgId") String orgId,
                                                                                      @Param("operatorUserIds") List<String> operatorUserIds);

    List<EmployeeFollowAnalysisDrilldownItemResponse> listWechatFriendDrilldown(@Param("request") EmployeeFollowAnalysisDrilldownRequest request,
                                                                                 @Param("orgId") String orgId,
                                                                                 @Param("operatorUserIds") List<String> operatorUserIds);

    List<EmployeeFollowAnalysisDrilldownItemResponse> listCallDrilldown(@Param("request") EmployeeFollowAnalysisDrilldownRequest request,
                                                                         @Param("orgId") String orgId,
                                                                         @Param("operatorUserIds") List<String> operatorUserIds);
}
