package cn.cordys.crm.customer.mapper;

import cn.cordys.crm.customer.dto.request.CustomerCallRecordPageRequest;
import cn.cordys.crm.customer.dto.response.CustomerCallRecordListResponse;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ExtCustomerCallRecordMapper {

    List<CustomerCallRecordListResponse> list(@Param("um") String um,
                                              @Param("customerTel") String customerTel,
                                              @Param("collectionTime") Long collectionTime,
                                              @Param("request") CustomerCallRecordPageRequest request,
                                              @Param("orgId") String orgId);
}
