package cn.cordys.mmba.mapper;

import cn.cordys.mmba.domain.MmbaCallRecordAudit;
import cn.cordys.mmba.domain.MmbaDeviceInfoAudit;
import cn.cordys.mmba.domain.MmbaSmsRecordAudit;
import cn.cordys.mmba.domain.MmbaWxAccountAudit;
import cn.cordys.mmba.domain.MmbaWxChatAudit;
import cn.cordys.mmba.domain.MmbaWxFriendChangeAudit;
import cn.cordys.mmba.domain.MmbaWxFriendListAudit;
import cn.cordys.mmba.domain.MmbaWxLoginAudit;
import cn.cordys.mmba.dto.CustomerCallStatusDTO;
import cn.cordys.mmba.dto.CustomerWxFriendStatusDTO;
import cn.cordys.mmba.dto.CustomerWxSendRouteDTO;
import cn.cordys.mmba.dto.request.MmbaCallRecordAuditPageRequest;
import cn.cordys.mmba.dto.request.MmbaDeviceInfoAuditPageRequest;
import cn.cordys.mmba.dto.request.MmbaSmsRecordAuditPageRequest;
import cn.cordys.mmba.dto.request.MmbaWxAccountAuditPageRequest;
import cn.cordys.mmba.dto.request.MmbaWxChatAuditPageRequest;
import cn.cordys.mmba.dto.request.MmbaWxFriendChangeAuditPageRequest;
import cn.cordys.mmba.dto.request.MmbaWxFriendListAuditPageRequest;
import cn.cordys.mmba.dto.request.MmbaWxLoginAuditPageRequest;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MMBA 审计查询扩展 Mapper。
 */
public interface ExtMmbaAuditMapper {

    List<CustomerCallStatusDTO> listCustomerCallStatus(@Param("customerTels") List<String> customerTels,
                                                       @Param("orgId") String orgId);

    List<CustomerWxFriendStatusDTO> listCustomerWxFriendStatus(@Param("friendSearches") List<String> friendSearches,
                                                               @Param("um") String um,
                                                               @Param("staffIdInApps") List<String> staffIdInApps);

    Boolean existsPendingAddFriendReceipt(@Param("um") String um,
                                          @Param("friendPhone") String friendPhone,
                                          @Param("umWxids") List<String> umWxids);

    CustomerWxSendRouteDTO getCustomerWxSendRoute(@Param("um") String um,
                                                  @Param("friendPhone") String friendPhone);

    List<MmbaCallRecordAudit> listCallRecord(@Param("request") MmbaCallRecordAuditPageRequest request,
                                             @Param("orgId") String orgId);

    List<MmbaSmsRecordAudit> listSmsRecord(@Param("request") MmbaSmsRecordAuditPageRequest request,
                                           @Param("orgId") String orgId);

    List<MmbaWxAccountAudit> listWxAccount(@Param("request") MmbaWxAccountAuditPageRequest request,
                                           @Param("orgId") String orgId);

    List<MmbaWxChatAudit> listWxChat(@Param("request") MmbaWxChatAuditPageRequest request,
                                     @Param("orgId") String orgId);

    List<MmbaWxFriendChangeAudit> listWxFriendChange(@Param("request") MmbaWxFriendChangeAuditPageRequest request,
                                                     @Param("orgId") String orgId);

    List<MmbaWxFriendListAudit> listWxFriendList(@Param("request") MmbaWxFriendListAuditPageRequest request,
                                                 @Param("orgId") String orgId);

    List<MmbaWxLoginAudit> listWxLogin(@Param("request") MmbaWxLoginAuditPageRequest request,
                                       @Param("orgId") String orgId);

    List<MmbaDeviceInfoAudit> listDeviceInfo(@Param("request") MmbaDeviceInfoAuditPageRequest request,
                                             @Param("orgId") String orgId);
}
