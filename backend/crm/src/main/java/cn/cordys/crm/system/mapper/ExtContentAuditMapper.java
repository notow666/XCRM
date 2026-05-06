package cn.cordys.crm.system.mapper;

import cn.cordys.crm.system.dto.request.WechatAccountStatPageRequest;
import cn.cordys.crm.system.dto.response.WechatAccountStatListResponse;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ExtContentAuditMapper {

    List<WechatAccountStatListResponse> listWechatAccountStat(@Param("request") WechatAccountStatPageRequest request,
                                                              @Param("orgId") String orgId);
}
