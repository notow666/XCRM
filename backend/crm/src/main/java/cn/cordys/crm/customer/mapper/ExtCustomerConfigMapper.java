package cn.cordys.crm.customer.mapper;

import cn.cordys.crm.customer.domain.CustomerFailReasonConfig;
import cn.cordys.crm.customer.domain.CustomerFollowWayConfig;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ExtCustomerConfigMapper {

    List<CustomerFailReasonConfig> getFailReasonList(@Param("orgId") String orgId);

    void insertFailReason(@Param("config") CustomerFailReasonConfig config);

    void updateFailReason(@Param("config") CustomerFailReasonConfig config);

    void deleteFailReason(@Param("id") String id, @Param("orgId") String orgId);

    int countFailReasonById(@Param("id") String id, @Param("orgId") String orgId);

    List<CustomerFollowWayConfig> getFollowWayList(@Param("orgId") String orgId);

    void insertFollowWay(@Param("config") CustomerFollowWayConfig config);

    void updateFollowWay(@Param("config") CustomerFollowWayConfig config);

    void deleteFollowWay(@Param("id") String id, @Param("orgId") String orgId);
}
