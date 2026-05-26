package cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.mapper;

import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.domain.EmployeeStatDay;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.domain.EmployeeStatEvent;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.request.EmployeeFollowAnalysisDrilldownRequest;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response.EmployeeFollowAnalysisCustomerContextRow;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response.EmployeeFollowAnalysisDrilldownItemResponse;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response.EmployeeFollowAnalysisEmployeeDimensionRow;
import cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.dto.response.EmployeeFollowAnalysisMetricRow;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface EmployeeStatAnalysisMapper {

    List<EmployeeStatDay> listHistoryDayRows(@Param("startDate") String startDate,
                                             @Param("endDate") String endDate,
                                             @Param("orgId") String orgId,
                                             @Param("ownerUserIds") List<String> ownerUserIds);

    List<EmployeeStatDay> listEventDayRows(@Param("statDate") String statDate);

    List<EmployeeFollowAnalysisMetricRow> listCallDayRows(@Param("startTime") Long startTime,
                                                          @Param("endTime") Long endTime);

    List<EmployeeFollowAnalysisMetricRow> listInboundRows(@Param("startTime") Long startTime,
                                                          @Param("endTime") Long endTime,
                                                          @Param("orgId") String orgId,
                                                          @Param("ownerUserIds") List<String> ownerUserIds);

    List<EmployeeFollowAnalysisMetricRow> listContactedRows(@Param("startTime") Long startTime,
                                                            @Param("endTime") Long endTime,
                                                            @Param("orgId") String orgId,
                                                            @Param("ownerUserIds") List<String> ownerUserIds);

    List<EmployeeFollowAnalysisMetricRow> listWechatRows(@Param("startTime") Long startTime,
                                                         @Param("endTime") Long endTime,
                                                         @Param("orgId") String orgId,
                                                         @Param("ownerUserIds") List<String> ownerUserIds);

    List<EmployeeFollowAnalysisMetricRow> listCallRows(@Param("startTime") Long startTime,
                                                       @Param("endTime") Long endTime,
                                                       @Param("orgId") String orgId,
                                                       @Param("ownerUserIds") List<String> ownerUserIds);

    List<EmployeeFollowAnalysisEmployeeDimensionRow> listCurrentEmployees(@Param("orgId") String orgId);

    List<EmployeeFollowAnalysisCustomerContextRow> listCustomerSourceRows(@Param("orgId") String orgId,
                                                                          @Param("customerIds") List<String> customerIds);

    List<EmployeeFollowAnalysisDrilldownItemResponse> listInboundCustomerDrilldown(@Param("request") EmployeeFollowAnalysisDrilldownRequest request,
                                                                                   @Param("orgId") String orgId,
                                                                                   @Param("ownerUserIds") List<String> ownerUserIds);

    List<EmployeeFollowAnalysisDrilldownItemResponse> listContactedCustomerDrilldown(@Param("request") EmployeeFollowAnalysisDrilldownRequest request,
                                                                                     @Param("orgId") String orgId,
                                                                                     @Param("ownerUserIds") List<String> ownerUserIds);

    List<EmployeeFollowAnalysisDrilldownItemResponse> listWechatFriendDrilldown(@Param("request") EmployeeFollowAnalysisDrilldownRequest request,
                                                                                @Param("orgId") String orgId,
                                                                                @Param("ownerUserIds") List<String> ownerUserIds);

    List<EmployeeFollowAnalysisDrilldownItemResponse> listCallDrilldown(@Param("request") EmployeeFollowAnalysisDrilldownRequest request,
                                                                        @Param("orgId") String orgId,
                                                                        @Param("ownerUserIds") List<String> ownerUserIds);

    List<EmployeeStatEvent> listPendingWechatFriendEvents(@Param("friendPhone") String friendPhone,
                                                          @Param("operatorUserIds") List<String> operatorUserIds);

    int confirmWechatFriendSuccessEvent(@Param("id") String id,
                                        @Param("esId") String esId,
                                        @Param("eventTime") Long eventTime,
                                        @Param("statDate") String statDate);
}
