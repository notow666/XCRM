package cn.cordys.crm.system.service;

import cn.cordys.common.pager.PageUtils;
import cn.cordys.common.pager.Pager;
import cn.cordys.crm.system.dto.request.WechatAccountStatPageRequest;
import cn.cordys.crm.system.dto.response.WechatAccountStatListResponse;
import cn.cordys.crm.system.mapper.ExtContentAuditMapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import jakarta.annotation.Resource;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(rollbackFor = Exception.class)
public class ContentAuditService {

    @Resource
    private ExtContentAuditMapper extContentAuditMapper;

    public Pager<List<WechatAccountStatListResponse>> listWechatAccountStat(WechatAccountStatPageRequest request, String orgId) {
        Page<Object> page = PageHelper.startPage(request.getCurrent(), request.getPageSize());
        List<WechatAccountStatListResponse> list = extContentAuditMapper.listWechatAccountStat(request, orgId);
        fillPlaceholders(list);
        return PageUtils.setPageInfo(page, list);
    }

    private void fillPlaceholders(List<WechatAccountStatListResponse> list) {
        if (CollectionUtils.isEmpty(list)) {
            return;
        }
        for (WechatAccountStatListResponse item : list) {
            item.setEmployeeName(defaultText(item.getEmployeeName()));
            item.setDepartmentName(defaultText(item.getDepartmentName()));
            item.setDeviceDisplay(buildDeviceDisplay(item.getDeviceName(), item.getDeviceStatus()));
            item.setWxNickName(defaultText(item.getWxNickName()));
            item.setWxAccount(defaultText(item.getWxAccount()));
            item.setFriendCount("-");
            item.setChatRecordCount("-");
        }
    }

    private String buildDeviceDisplay(String deviceName, Integer deviceStatus) {
        String name = defaultText(deviceName);
        if (deviceStatus == null || deviceStatus == 1 || "-".equals(name)) {
            return name;
        }
        return name + "（" + getDeviceStatusText(deviceStatus) + "）";
    }

    private String getDeviceStatusText(Integer deviceStatus) {
        if (deviceStatus == null) {
            return "-";
        }
        return switch (deviceStatus) {
            case 1 -> "正常";
            case 3 -> "预注册";
            case 4 -> "已丢失";
            case 5 -> "待删除";
            case 6 -> "闲置";
            case 7 -> "已擦除";
            case 8 -> "待擦除";
            case 9 -> "已删除";
            default -> "未知状态";
        };
    }

    private String defaultText(String value) {
        return StringUtils.defaultIfBlank(value, "-");
    }
}
