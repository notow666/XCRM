package cn.cordys.crm.customer.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ExtCustomerDataCleanupMapper {

    List<CleanupFieldInfo> selectCleanupFieldInfoList(@Param("customerIds") List<String> customerIds,
                                                      @Param("fieldIds") List<String> fieldIds);

    int clearFieldValueByResourceAndFieldIds(@Param("customerIds") List<String> customerIds,
                                              @Param("fieldIds") List<String> fieldIds);

    int clearFieldBlobValueByResourceAndFieldIds(@Param("customerIds") List<String> customerIds,
                                                   @Param("fieldIds") List<String> fieldIds);

    record CleanupFieldInfo(String id, String resourceId, String fieldId, String fieldName) {}
}