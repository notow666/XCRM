package cn.cordys.mmba;

import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.common.dto.OptionDTO;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.pager.PagerWithOption;
import cn.cordys.common.util.Translator;
import cn.cordys.context.OrganizationContext;
import cn.cordys.mmba.domain.MmbaCallRecordAudit;
import cn.cordys.mmba.domain.MmbaCommandResult;
import cn.cordys.mmba.domain.MmbaDevice;
import cn.cordys.mmba.domain.MmbaDeviceInfoAudit;
import cn.cordys.mmba.domain.MmbaDeviceStatusAudit;
import cn.cordys.mmba.domain.MmbaSmsRecordAudit;
import cn.cordys.mmba.domain.MmbaWxAccountAudit;
import cn.cordys.mmba.domain.MmbaWxChatAudit;
import cn.cordys.mmba.domain.MmbaWxFriendChangeAudit;
import cn.cordys.mmba.domain.MmbaWxFriendListAudit;
import cn.cordys.mmba.domain.MmbaWxLoginAudit;
import cn.cordys.mmba.dto.request.MmbaCallRecordAuditPageRequest;
import cn.cordys.mmba.dto.request.MmbaCommandResultPageRequest;
import cn.cordys.mmba.dto.request.MmbaDeviceInfoAuditPageRequest;
import cn.cordys.mmba.dto.request.MmbaDevicePageRequest;
import cn.cordys.mmba.dto.request.MmbaDeviceSaveRequest;
import cn.cordys.mmba.dto.request.MmbaDeviceStatusAuditPageRequest;
import cn.cordys.mmba.dto.request.MmbaDeviceUpdateRequest;
import cn.cordys.mmba.dto.response.MmbaDeviceImportResponse;
import cn.cordys.mmba.dto.request.MmbaSmsRecordAuditPageRequest;
import cn.cordys.mmba.dto.request.MmbaWxAccountAuditPageRequest;
import cn.cordys.mmba.dto.request.MmbaWxChatAuditPageRequest;
import cn.cordys.mmba.dto.request.MmbaWxFriendChangeAuditPageRequest;
import cn.cordys.mmba.dto.request.MmbaWxFriendListAuditPageRequest;
import cn.cordys.mmba.dto.request.MmbaWxLoginAuditPageRequest;
import cn.cordys.mmba.service.MmbaDeviceImportService;
import cn.cordys.mmba.service.MmbaDeviceService;
import cn.cordys.mmba.service.MmbaFacadeService;
import cn.cordys.mmba.service.MmbaQueryService;
import cn.cordys.security.SessionUtils;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * CRM 内部使用的 MMBA 接入控制器。
 * 发送类接口统一走门面服务，查询类接口统一走分页查询服务。
 */
@RestController
@RequestMapping("/mmba")
@Tag(name = "MMBA接口")
public class MmbaController {

    @Resource
    private MmbaFacadeService mmbaFacadeService;
    @Resource
    private MmbaQueryService mmbaQueryService;
    @Resource
    private MmbaDeviceService mmbaDeviceService;
    @Resource
    private MmbaDeviceImportService mmbaDeviceImportService;

    /**
     * 拨打电话。
     */
    @PostMapping("/phone/dial")
    @Operation(summary = "拨打电话")
    public JsonNode dial(@RequestBody JsonNode request) {
        return mmbaFacadeService.dial(request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    /**
     * 限制电话拨打。
     */
    @PostMapping("/phone/limit")
    @Operation(summary = "限制电话拨打")
    public JsonNode callLimit(@RequestBody JsonNode request) {
        return mmbaFacadeService.callLimit(request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    /**
     * 发送短信。
     */
    @PostMapping("/sms/send")
    @Operation(summary = "发送短信")
    public JsonNode sendSms(@RequestBody JsonNode request) {
        return mmbaFacadeService.sendSms(request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    /**
     * 发送微信消息。
     */
    @PostMapping("/wx/message/send")
    @Operation(summary = "发送微信消息")
    public JsonNode sendWxMsg(@RequestBody JsonNode request) {
        return mmbaFacadeService.sendWxMsg(request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    /**
     * 添加微信好友。
     */
    @PostMapping("/wx/friend/add")
    @Operation(summary = "添加微信好友")
    public JsonNode addWxFriend(@RequestBody JsonNode request) {
        return mmbaFacadeService.addWxFriend(request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    /**
     * 发送朋友圈。
     */
    @PostMapping("/wx/moment/send")
    @Operation(summary = "发送朋友圈")
    public JsonNode sendWxMoment(@RequestBody JsonNode request) {
        return mmbaFacadeService.sendWxMoment(request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    /**
     * 修改微信好友备注或描述。
     */
    @PostMapping("/wx/friend/remark/modify")
    @Operation(summary = "修改微信好友备注/描述")
    public JsonNode modifyWxFriendRemark(@RequestBody JsonNode request) {
        return mmbaFacadeService.modifyWxFriendRemark(request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    /**
     * 查询已登录微信账号。
     */
    @PostMapping("/wx/account/login/query")
    @Operation(summary = "已登录微信账号查询")
    public JsonNode queryLoginWxAccount(@RequestBody JsonNode request) {
        return mmbaFacadeService.queryLoginWxAccount(request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    /**
     * 查询微信好友列表。
     */
    @PostMapping("/wx/friend/list/query")
    @Operation(summary = "微信好友列表查询")
    public JsonNode queryWxFriendList(@RequestBody JsonNode request) {
        return mmbaFacadeService.queryWxFriendList(request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    /**
     * 查询重复微信好友。
     */
    @PostMapping("/wx/friend/duplicate/query")
    @Operation(summary = "重复微信好友查询")
    public JsonNode queryDuplicateWxFriend(@RequestBody JsonNode request) {
        return mmbaFacadeService.queryDuplicateWxFriend(request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    /**
     * 查询微信聊天记录。
     */
    @PostMapping("/wx/chat/query")
    @Operation(summary = "微信聊天记录查询")
    public JsonNode queryChatMessage(@RequestBody JsonNode request) {
        return mmbaFacadeService.queryChatMessage(request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    /**
     * 设备消息推送。
     */
    @PostMapping("/device/message/push")
    @Operation(summary = "设备消息推送")
    public JsonNode pushDeviceMessage(@RequestBody JsonNode request) {
        return mmbaFacadeService.pushDeviceMessage(request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @PostMapping("/device/list/query")
    @Operation(summary = "设备列表查询")
    public JsonNode queryDeviceList(@RequestBody JsonNode request) {
        return mmbaFacadeService.queryDeviceList(request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    /**
     * 下载带鉴权的 MMBA 文件。
     */
    @PostMapping("/file/download")
    @Operation(summary = "下载MMBA文件")
    public void fetchFile(@RequestBody JsonNode request, HttpServletResponse response) throws IOException {
        byte[] bytes = mmbaFacadeService.fetchFile(request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
        writeBinaryResponse(response, bytes);
    }

    /**
     * 查询设备最新快照列表。
     */
    @PostMapping("/device/page")
    @Operation(summary = "MMBA设备列表")
    @RequiresPermissions(PermissionConstants.MMBA_DEVICE_READ)
    public PagerWithOption<List<MmbaDevice>> pageDevice(@Valid @RequestBody MmbaDevicePageRequest request) {
        return mmbaQueryService.pageDevice(request, OrganizationContext.getOrganizationId());
    }

    @GetMapping("/device/list")
    @Operation(summary = "MMBA设备列表")
    public List<OptionDTO> listDevice() {
        return mmbaQueryService.listDevice();
    }

    @GetMapping("/device/{id}")
    @Operation(summary = "MMBA设备详情")
    @RequiresPermissions(PermissionConstants.MMBA_DEVICE_READ)
    public MmbaDevice getDevice(@PathVariable("id") String id) {
        MmbaDevice device = mmbaDeviceService.getDevice(id);
        if (device == null) {
            throw new GenericException(Translator.get("mmba_device_not_found"));
        }
        return device;
    }

    @PostMapping("/device/add")
    @Operation(summary = "MMBA设备新增")
    @RequiresPermissions(PermissionConstants.MMBA_DEVICE_ADD)
    public MmbaDevice addDevice(@Valid @RequestBody MmbaDeviceSaveRequest request) {
        return mmbaDeviceService.addDevice(request, SessionUtils.getUserId());
    }

    @PostMapping("/device/update")
    @Operation(summary = "MMBA设备修改")
    @RequiresPermissions(PermissionConstants.MMBA_DEVICE_UPDATE)
    public MmbaDevice updateDevice(@Valid @RequestBody MmbaDeviceUpdateRequest request) {
        return mmbaDeviceService.updateDevice(request, SessionUtils.getUserId());
    }

    @PostMapping("/device/import")
    @Operation(summary = "MMBA设备Excel导入")
    @RequiresPermissions(PermissionConstants.MMBA_DEVICE_IMPORT)
    public MmbaDeviceImportResponse importDevices(@RequestPart("file") MultipartFile file) {
        return mmbaDeviceImportService.importExcel(file, SessionUtils.getUserId());
    }

    /**
     * 查询发送指令最终结果。
     */
    @PostMapping("/command-result/page")
    @Operation(summary = "MMBA指令结果列表")
    public PagerWithOption<List<MmbaCommandResult>> pageCommandResult(@Valid @RequestBody MmbaCommandResultPageRequest request) {
        return mmbaQueryService.pageCommandResult(request, OrganizationContext.getOrganizationId());
    }

    /**
     * 查询通话审计列表。
     */
    @PostMapping("/call/audit/page")
    @Operation(summary = "通话审计列表")
    public PagerWithOption<List<MmbaCallRecordAudit>> pageCallAudit(@Valid @RequestBody MmbaCallRecordAuditPageRequest request) {
        return mmbaQueryService.pageCallAudit(request, OrganizationContext.getOrganizationId());
    }

    /**
     * 查询短信审计列表。
     */
    @PostMapping("/sms/audit/page")
    @Operation(summary = "短信审计列表")
    public PagerWithOption<List<MmbaSmsRecordAudit>> pageSmsAudit(@Valid @RequestBody MmbaSmsRecordAuditPageRequest request) {
        return mmbaQueryService.pageSmsAudit(request, OrganizationContext.getOrganizationId());
    }

    /**
     * 查询微信账号审计列表。
     */
    @PostMapping("/wx/account/audit/page")
    @Operation(summary = "微信账号审计列表")
    public PagerWithOption<List<MmbaWxAccountAudit>> pageWxAccountAudit(@Valid @RequestBody MmbaWxAccountAuditPageRequest request) {
        return mmbaQueryService.pageWxAccountAudit(request, OrganizationContext.getOrganizationId());
    }

    /**
     * 查询微信聊天审计列表。
     */
    @PostMapping("/wx/chat/audit/page")
    @Operation(summary = "微信聊天审计列表")
    public PagerWithOption<List<MmbaWxChatAudit>> pageWxChatAudit(@Valid @RequestBody MmbaWxChatAuditPageRequest request) {
        return mmbaQueryService.pageWxChatAudit(request, OrganizationContext.getOrganizationId());
    }

    /**
     * 查询微信好友变更审计列表。
     */
    @PostMapping("/wx/friend/change/audit/page")
    @Operation(summary = "微信好友变更审计列表")
    public PagerWithOption<List<MmbaWxFriendChangeAudit>> pageWxFriendChangeAudit(@Valid @RequestBody MmbaWxFriendChangeAuditPageRequest request) {
        return mmbaQueryService.pageWxFriendChangeAudit(request, OrganizationContext.getOrganizationId());
    }

    /**
     * 查询微信好友列表审计。
     */
    @PostMapping("/wx/friend/list/audit/page")
    @Operation(summary = "微信好友列表审计")
    public PagerWithOption<List<MmbaWxFriendListAudit>> pageWxFriendListAudit(@Valid @RequestBody MmbaWxFriendListAuditPageRequest request) {
        return mmbaQueryService.pageWxFriendListAudit(request, OrganizationContext.getOrganizationId());
    }

    /**
     * 查询微信登录登出审计。
     */
    @PostMapping("/wx/login/audit/page")
    @Operation(summary = "微信登录登出审计")
    public PagerWithOption<List<MmbaWxLoginAudit>> pageWxLoginAudit(@Valid @RequestBody MmbaWxLoginAuditPageRequest request) {
        return mmbaQueryService.pageWxLoginAudit(request, OrganizationContext.getOrganizationId());
    }

    /**
     * 查询设备信息审计。
     */
    @PostMapping("/device/info/audit/page")
    @Operation(summary = "设备信息审计")
    public PagerWithOption<List<MmbaDeviceInfoAudit>> pageDeviceInfoAudit(@Valid @RequestBody MmbaDeviceInfoAuditPageRequest request) {
        return mmbaQueryService.pageDeviceInfoAudit(request, OrganizationContext.getOrganizationId());
    }

    /**
     * 查询设备状态变更审计。
     */
    @PostMapping("/device/status/audit/page")
    @Operation(summary = "设备状态审计列表")
    public PagerWithOption<List<MmbaDeviceStatusAudit>> pageDeviceStatusAudit(@Valid @RequestBody MmbaDeviceStatusAuditPageRequest request) {
        return mmbaQueryService.pageDeviceStatusAudit(request, OrganizationContext.getOrganizationId());
    }

    /**
     * 将下载结果直接写回响应流。
     */
    private void writeBinaryResponse(HttpServletResponse response, byte[] bytes) throws IOException {
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        if (bytes == null) {
            response.setContentLength(0);
            return;
        }
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
        response.flushBuffer();
    }
}
