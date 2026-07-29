package cn.cordys.crm.report.customerrecording.mapper;

import cn.cordys.crm.report.customerrecording.dto.request.CustomerRecordingPageRequest;
import cn.cordys.crm.report.customerrecording.dto.response.CustomerRecordingEmployeeOptionResponse;
import cn.cordys.crm.report.customerrecording.dto.response.CustomerRecordingListResponse;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CustomerRecordingMapper {

    List<CustomerRecordingEmployeeOptionResponse> listCurrentEmployees();

    List<CustomerRecordingListResponse> page(@Param("request") CustomerRecordingPageRequest request,
                                               @Param("operatorUserIds") List<String> operatorUserIds);
}
