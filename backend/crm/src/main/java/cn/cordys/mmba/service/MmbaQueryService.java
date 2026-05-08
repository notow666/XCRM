package cn.cordys.mmba.service;

import cn.cordys.common.dto.OptionDTO;
import cn.cordys.common.pager.PageUtils;
import cn.cordys.common.pager.PagerWithOption;
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
import cn.cordys.mmba.dto.request.MmbaDeviceStatusAuditPageRequest;
import cn.cordys.mmba.dto.request.MmbaSmsRecordAuditPageRequest;
import cn.cordys.mmba.dto.request.MmbaWxAccountAuditPageRequest;
import cn.cordys.mmba.dto.request.MmbaWxChatAuditPageRequest;
import cn.cordys.mmba.dto.request.MmbaWxFriendChangeAuditPageRequest;
import cn.cordys.mmba.dto.request.MmbaWxFriendListAuditPageRequest;
import cn.cordys.mmba.dto.request.MmbaWxLoginAuditPageRequest;
import cn.cordys.mmba.excel.MmbaDeviceImportDict;
import cn.cordys.mmba.mapper.ExtMmbaAuditMapper;
import cn.cordys.mmba.mapper.ExtMmbaCommandResultMapper;
import cn.cordys.mmba.mapper.ExtMmbaDeviceMapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MMBA 查询服务。
 * 当前阶段提供设备快照、指令结果和各类审计数据的分页读取能力。
 */
@Service
public class MmbaQueryService {

    private static final Map<String, List<OptionDTO>> EMPTY_OPTIONS = Collections.emptyMap();

    @Resource
    private ExtMmbaDeviceMapper extMmbaDeviceMapper;
    @Resource
    private ExtMmbaCommandResultMapper extMmbaCommandResultMapper;
    @Resource
    private ExtMmbaAuditMapper extMmbaAuditMapper;

    /**
     * 查询设备当前快照列表。
     */
    public PagerWithOption<List<MmbaDevice>> pageDevice(MmbaDevicePageRequest request, String organizationId) {
        Page<Object> page = PageHelper.startPage(request.getCurrent(), request.getPageSize());
        List<MmbaDevice> list = extMmbaDeviceMapper.page(request, organizationId);
        return PageUtils.setPageInfoWithOption(page, list, EMPTY_OPTIONS);
    }

    public List<OptionDTO> listDevice() {
        List<MmbaDevice> list = extMmbaDeviceMapper.list();
        if(CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }
        return list.stream()
                .map(d -> {
                    return new OptionDTO(d.getUm(), d.getUm() + "-" + MmbaDeviceImportDict.parseDeviceStatus(d.getDeviceStatus()));
                }).collect(Collectors.toList());
    }

    /**
     * 查询发送类接口的异步执行结果。
     */
    public PagerWithOption<List<MmbaCommandResult>> pageCommandResult(MmbaCommandResultPageRequest request, String organizationId) {
        Page<Object> page = PageHelper.startPage(request.getCurrent(), request.getPageSize());
        List<MmbaCommandResult> list = extMmbaCommandResultMapper.list(request, organizationId);
        return PageUtils.setPageInfoWithOption(page, list, EMPTY_OPTIONS);
    }

    /**
     * 查询通话审计流水。
     */
    public PagerWithOption<List<MmbaCallRecordAudit>> pageCallAudit(MmbaCallRecordAuditPageRequest request, String organizationId) {
        Page<Object> page = PageHelper.startPage(request.getCurrent(), request.getPageSize());
        List<MmbaCallRecordAudit> list = extMmbaAuditMapper.listCallRecord(request, organizationId);
        return PageUtils.setPageInfoWithOption(page, list, EMPTY_OPTIONS);
    }

    /**
     * 查询短信审计流水。
     */
    public PagerWithOption<List<MmbaSmsRecordAudit>> pageSmsAudit(MmbaSmsRecordAuditPageRequest request, String organizationId) {
        Page<Object> page = PageHelper.startPage(request.getCurrent(), request.getPageSize());
        List<MmbaSmsRecordAudit> list = extMmbaAuditMapper.listSmsRecord(request, organizationId);
        return PageUtils.setPageInfoWithOption(page, list, EMPTY_OPTIONS);
    }

    /**
     * 查询微信账号审计流水。
     */
    public PagerWithOption<List<MmbaWxAccountAudit>> pageWxAccountAudit(MmbaWxAccountAuditPageRequest request, String organizationId) {
        Page<Object> page = PageHelper.startPage(request.getCurrent(), request.getPageSize());
        List<MmbaWxAccountAudit> list = extMmbaAuditMapper.listWxAccount(request, organizationId);
        return PageUtils.setPageInfoWithOption(page, list, EMPTY_OPTIONS);
    }

    /**
     * 查询微信聊天审计流水。
     */
    public PagerWithOption<List<MmbaWxChatAudit>> pageWxChatAudit(MmbaWxChatAuditPageRequest request, String organizationId) {
        Page<Object> page = PageHelper.startPage(request.getCurrent(), request.getPageSize());
        List<MmbaWxChatAudit> list = extMmbaAuditMapper.listWxChat(request, organizationId);
        return PageUtils.setPageInfoWithOption(page, list, EMPTY_OPTIONS);
    }

    /**
     * 查询微信好友变更审计流水。
     */
    public PagerWithOption<List<MmbaWxFriendChangeAudit>> pageWxFriendChangeAudit(MmbaWxFriendChangeAuditPageRequest request, String organizationId) {
        Page<Object> page = PageHelper.startPage(request.getCurrent(), request.getPageSize());
        List<MmbaWxFriendChangeAudit> list = extMmbaAuditMapper.listWxFriendChange(request, organizationId);
        return PageUtils.setPageInfoWithOption(page, list, EMPTY_OPTIONS);
    }

    /**
     * 查询微信好友列表审计流水。
     */
    public PagerWithOption<List<MmbaWxFriendListAudit>> pageWxFriendListAudit(MmbaWxFriendListAuditPageRequest request, String organizationId) {
        Page<Object> page = PageHelper.startPage(request.getCurrent(), request.getPageSize());
        List<MmbaWxFriendListAudit> list = extMmbaAuditMapper.listWxFriendList(request, organizationId);
        return PageUtils.setPageInfoWithOption(page, list, EMPTY_OPTIONS);
    }

    /**
     * 查询微信登录登出审计流水。
     */
    public PagerWithOption<List<MmbaWxLoginAudit>> pageWxLoginAudit(MmbaWxLoginAuditPageRequest request, String organizationId) {
        Page<Object> page = PageHelper.startPage(request.getCurrent(), request.getPageSize());
        List<MmbaWxLoginAudit> list = extMmbaAuditMapper.listWxLogin(request, organizationId);
        return PageUtils.setPageInfoWithOption(page, list, EMPTY_OPTIONS);
    }

    /**
     * 查询设备信息审计流水。
     */
    public PagerWithOption<List<MmbaDeviceInfoAudit>> pageDeviceInfoAudit(MmbaDeviceInfoAuditPageRequest request, String organizationId) {
        Page<Object> page = PageHelper.startPage(request.getCurrent(), request.getPageSize());
        List<MmbaDeviceInfoAudit> list = extMmbaAuditMapper.listDeviceInfo(request, organizationId);
        return PageUtils.setPageInfoWithOption(page, list, EMPTY_OPTIONS);
    }

    /**
     * 查询设备状态审计流水。
     */
    public PagerWithOption<List<MmbaDeviceStatusAudit>> pageDeviceStatusAudit(MmbaDeviceStatusAuditPageRequest request, String organizationId) {
        Page<Object> page = PageHelper.startPage(request.getCurrent(), request.getPageSize());
        List<MmbaDeviceStatusAudit> list = extMmbaAuditMapper.listDeviceStatus(request, organizationId);
        return PageUtils.setPageInfoWithOption(page, list, EMPTY_OPTIONS);
    }
}
