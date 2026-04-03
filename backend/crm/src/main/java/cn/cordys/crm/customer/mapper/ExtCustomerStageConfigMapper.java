package cn.cordys.crm.customer.mapper;

import cn.cordys.crm.customer.domain.CustomerStageConfig;
import cn.cordys.crm.customer.dto.request.CustomerStageAddRequest;
import cn.cordys.crm.customer.dto.request.CustomerStageRollBackRequest;
import cn.cordys.crm.customer.dto.request.CustomerStageUpdateRequest;
import cn.cordys.crm.opportunity.dto.response.StageConfigResponse;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ExtCustomerStageConfigMapper {

    int countStageConfig(@Param("orgId") String orgId);

    void moveUpStageConfig(@Param("start") Long start, @Param("orgId") String orgId, @Param("pos") Long pos);

    void moveDownStageConfig(@Param("start") Long start, @Param("orgId") String orgId, @Param("pos") Long pos);

    List<StageConfigResponse> getStageConfigList(@Param("orgId") String orgId);

    void updateRollBack(@Param("request") CustomerStageRollBackRequest request, @Param("orgId") String orgId);

    void updateStageConfig(@Param("request") CustomerStageUpdateRequest request, @Param("userId") String userId);

    List<CustomerStageConfig> getAllStageConfigList();

    void moveUp(@Param("start") Long start, @Param("end") Long end, @Param("orgId") String orgId, @Param("defaultPos") Long defaultPos);

    void moveDown(@Param("start") Long start, @Param("end") Long end, @Param("orgId") String orgId, @Param("defaultPos") Long defaultPos);

    void updatePos(@Param("id") String id, @Param("pos") Long pos);

    int countByType(@Param("type") String type, @Param("orgId") String orgId);

    int countByStage(@Param("stageId") String stageId, @Param("orgId") String orgId);
}