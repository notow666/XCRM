package cn.cordys.mmba.service;

import cn.cordys.aspectj.constants.LogModule;
import cn.cordys.aspectj.constants.LogType;
import cn.cordys.aspectj.dto.LogDTO;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.exception.GenericException;
import cn.cordys.crm.system.domain.OrganizationUser;
import cn.cordys.crm.system.domain.User;
import cn.cordys.crm.system.service.LogService;
import cn.cordys.mmba.MmbaConstants;
import cn.cordys.mmba.domain.MmbaCallLogCleanConfig;
import cn.cordys.mmba.domain.MmbaCallLogCleanConfigUser;
import cn.cordys.mmba.dto.request.MmbaCallLogCleanConfigSaveRequest;
import cn.cordys.mmba.dto.response.MmbaCallLogCleanConfigResponse;
import cn.cordys.mmba.dto.response.MmbaCallLogCleanResponse;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MmbaCallLogCleanService {

    @Resource
    private BaseMapper<MmbaCallLogCleanConfig> configMapper;
    @Resource
    private BaseMapper<MmbaCallLogCleanConfigUser> configUserMapper;
    @Resource
    private BaseMapper<OrganizationUser> organizationUserMapper;
    @Resource
    private BaseMapper<User> userMapper;
    @Resource
    private MmbaFacadeService mmbaFacadeService;
    @Resource
    private LogService logService;

    public MmbaCallLogCleanResponse executeManual(List<String> userIds, String operatorId, String organizationId) {
        return execute(userIds, operatorId, organizationId, false);
    }

    /**
     * 定时任务和手动操作共用同一发送逻辑。定时请求使用系统用户作为操作人，回执据此不发送通知。
     */
    public MmbaCallLogCleanResponse executeScheduled(String organizationId) {
        MmbaCallLogCleanConfig config = findConfig(organizationId);
        if (config == null || !Boolean.TRUE.equals(config.getEnable())) {
            log.info("定时清除通话记录跳过，配置未开启 organizationId={}", organizationId);
            return new MmbaCallLogCleanResponse(0, 0, 0, 0);
        }
        List<String> userIds = listConfigUserIds(config.getId());
        return execute(userIds, MmbaConstants.SYSTEM_USER, organizationId, true);
    }

    private MmbaCallLogCleanResponse execute(List<String> userIds, String operatorId, String organizationId, boolean scheduled) {
        List<User> users = listOrganizationUsers(userIds, organizationId);
        int issued = 0;
        int skipped = 0;
        int failed = 0;
        log.info("开始{}清除通话记录 organizationId={} operatorId={} selectedCount={}",
                scheduled ? "定时" : "手动", organizationId, operatorId, users.size());
        for (User user : users) {
            String um = StringUtils.trimToNull(user.getUm());
            if (um == null) {
                skipped++;
                log.info("清除通话记录跳过，员工UM为空 organizationId={} userId={}", organizationId, user.getId());
                continue;
            }
            try {
                mmbaFacadeService.cleanCallLog(buildRequest(user), operatorId, organizationId);
                issued++;
            } catch (Exception e) {
                failed++;
                // 批量操作必须逐人隔离，单个员工失败不能中断后续员工。
                log.error("清除通话记录指令下发失败 organizationId={} userId={} um={} scheduled={}",
                        organizationId, user.getId(), um, scheduled, e);
            }
        }
        if (!scheduled) {
            addExecutionLog(users, operatorId, organizationId, false);
        }
        log.info("{}清除通话记录下发完成 organizationId={} selectedCount={} issuedCount={} skippedCount={} failedCount={}",
                scheduled ? "定时" : "手动", organizationId, users.size(), issued, skipped, failed);
        return new MmbaCallLogCleanResponse(users.size(), issued, skipped, failed);
    }

    private ObjectNode buildRequest(User user) {
        ObjectNode request = JsonNodeFactory.instance.objectNode();
        request.put("um", user.getUm());
        // 保存 CRM 员工快照
        ObjectNode bizExtInfo = request.putObject("bizExtInfo");
        bizExtInfo.put("target_user_id", user.getId());
        bizExtInfo.put("target_user_name", StringUtils.defaultString(user.getName()));
        return request;
    }

    public MmbaCallLogCleanConfigResponse getConfig(String organizationId) {
        MmbaCallLogCleanConfig config = findConfig(organizationId);
        return config == null
                ? new MmbaCallLogCleanConfigResponse(false, List.of())
                : new MmbaCallLogCleanConfigResponse(Boolean.TRUE.equals(config.getEnable()), listConfigUserIds(config.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveConfig(MmbaCallLogCleanConfigSaveRequest request, String operatorId, String organizationId) {
        List<User> validUsers = listOrganizationUsers(request.getUserIds(), organizationId);
        Set<String> validUserIds = validUsers.stream().map(User::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (Boolean.TRUE.equals(request.getEnable()) && validUserIds.isEmpty()) {
            throw new GenericException("开启定时清除通话记录时必须至少选择一名员工");
        }
        long now = System.currentTimeMillis();
        MmbaCallLogCleanConfig config = findConfig(organizationId);
        if (config == null) {
            config = new MmbaCallLogCleanConfig();
            config.setId(IDGenerator.nextStr());
            config.setOrganizationId(organizationId);
            config.setCreateUser(operatorId);
            config.setCreateTime(now);
        }
        config.setEnable(request.getEnable());
        config.setUpdateUser(operatorId);
        config.setUpdateTime(now);
        if (configMapper.selectByPrimaryKey(config.getId()) == null) {
            configMapper.insert(config);
        } else {
            configMapper.update(config);
        }
        LambdaQueryWrapper<MmbaCallLogCleanConfigUser> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(MmbaCallLogCleanConfigUser::getConfigId, config.getId());
        configUserMapper.deleteByLambda(deleteWrapper);
        for (String userId : validUserIds) {
            MmbaCallLogCleanConfigUser relation = new MmbaCallLogCleanConfigUser();
            relation.setId(IDGenerator.nextStr());
            relation.setConfigId(config.getId());
            relation.setUserId(userId);
            relation.setCreateUser(operatorId);
            relation.setCreateTime(now);
            configUserMapper.insert(relation);
        }
        addConfigLog(config, validUsers, operatorId, organizationId);
        log.info("定时清除通话记录配置保存完成 organizationId={} operatorId={} enable={} userCount={}",
                organizationId, operatorId, request.getEnable(), validUserIds.size());
    }

    private List<User> listOrganizationUsers(List<String> userIds, String organizationId) {
        if (CollectionUtils.isEmpty(userIds)) {
            return List.of();
        }
        Set<String> distinctIds = new LinkedHashSet<>(userIds);
        Map<String, OrganizationUser> relationByInputId = new LinkedHashMap<>();

        // 组织架构表格的行主键是 sys_organization_user.id，配置选择器返回的是 sys_user.id，需同时兼容。
        organizationUserMapper.selectByIds(distinctIds.toArray(new String[0])).stream()
                .filter(relation -> organizationId.equals(relation.getOrganizationId()))
                .forEach(relation -> relationByInputId.put(relation.getId(), relation));
        LambdaQueryWrapper<OrganizationUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrganizationUser::getOrganizationId, organizationId).in(OrganizationUser::getUserId, distinctIds);
        organizationUserMapper.selectListByLambda(wrapper)
                .forEach(relation -> relationByInputId.put(relation.getUserId(), relation));
        Set<String> organizationUserIds = relationByInputId.values().stream()
                .map(OrganizationUser::getUserId).collect(Collectors.toSet());
        if (organizationUserIds.isEmpty()) {
            return List.of();
        }
        Map<String, User> userMap = userMapper.selectByIds(organizationUserIds.toArray(new String[0])).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        List<User> result = new ArrayList<>();
        for (String inputId : distinctIds) {
            OrganizationUser relation = relationByInputId.get(inputId);
            User user = relation == null ? null : userMap.get(relation.getUserId());
            if (user != null) {
                result.add(user);
            }
        }
        return result;
    }

    private MmbaCallLogCleanConfig findConfig(String organizationId) {
        MmbaCallLogCleanConfig query = new MmbaCallLogCleanConfig();
        query.setOrganizationId(organizationId);
        return configMapper.selectOne(query);
    }

    private List<String> listConfigUserIds(String configId) {
        MmbaCallLogCleanConfigUser query = new MmbaCallLogCleanConfigUser();
        query.setConfigId(configId);
        return configUserMapper.select(query).stream().map(MmbaCallLogCleanConfigUser::getUserId).toList();
    }

    private void addExecutionLog(List<User> users, String operatorId, String organizationId, boolean scheduled) {
        List<String> employeeNames = users.stream().map(User::getName).filter(StringUtils::isNotBlank).toList();
        String resourceName = employeeNames.isEmpty() ? "清除通话记录"
                : employeeNames.size() == 1 ? employeeNames.get(0) : employeeNames.get(0) + " 等 " + employeeNames.size() + " 人";
        // 操作对象展示员工姓名摘要，完整员工范围保存到日志 Blob，避免定长字段截断或暴露内部员工 ID。
        LogDTO logDTO = new LogDTO(organizationId, organizationId,
                operatorId, scheduled ? LogType.SYNC : LogType.CLEAN, LogModule.SYSTEM_ORGANIZATION,
                resourceName);
        Map<String, Object> employeeScope = new LinkedHashMap<>();
        employeeScope.put("employeeScope", employeeNames);
        logDTO.setModifiedValue(employeeScope);
        logService.add(logDTO);
    }

    private void addConfigLog(MmbaCallLogCleanConfig config, List<User> users, String operatorId, String organizationId) {
        LogDTO logDTO = new LogDTO(organizationId, config.getId(), operatorId, LogType.UPDATE,
                LogModule.SYSTEM_MODULE, "定时清除通话记录配置");
        Map<String, Object> modified = new LinkedHashMap<>();
        // 使用本功能专用字段，避免改变系统模块中其他日志的字段转换行为。
        modified.put("callLogClean.enable", config.getEnable());
        // 日志保存员工姓名快照，详情页不展示内部用户 ID，员工后续改名也不影响历史审计记录。
        modified.put("callLogClean.employeeScope", users.stream()
                .map(User::getName).filter(StringUtils::isNotBlank).toList());
        logDTO.setModifiedValue(modified);
        logService.add(logDTO);
    }
}
