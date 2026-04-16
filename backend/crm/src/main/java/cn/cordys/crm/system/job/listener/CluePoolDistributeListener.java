package cn.cordys.crm.system.job.listener;

import cn.cordys.common.constants.FormKey;
import cn.cordys.common.constants.InternalUser;
import cn.cordys.common.dto.condition.BaseCondition;
import cn.cordys.common.dto.condition.CombineSearch;
import cn.cordys.common.dto.condition.FilterCondition;
import cn.cordys.common.utils.ConditionFilterUtils;
import cn.cordys.common.util.JSON;
import cn.cordys.common.pager.PagerWithOption;
import cn.cordys.crm.clue.domain.Clue;
import cn.cordys.crm.clue.domain.CluePool;
import cn.cordys.crm.clue.domain.CluePoolDistributeRule;
import cn.cordys.crm.clue.dto.request.CluePageRequest;
import cn.cordys.crm.clue.dto.response.ClueListResponse;
import cn.cordys.crm.clue.service.CluePoolService;
import cn.cordys.crm.clue.service.ClueService;
import cn.cordys.crm.customer.domain.CustomerPool;
import cn.cordys.crm.system.dto.RuleConditionDTO;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 线索池自动分发：将线索池内满足条件的线索转客户并移入指定公海
 */
@Component
@Slf4j
public class CluePoolDistributeListener implements ApplicationListener<ExecuteEvent> {

    @Resource
    private BaseMapper<Clue> clueMapper;
    @Resource
    private BaseMapper<CluePool> cluePoolMapper;
    @Resource
    private BaseMapper<CluePoolDistributeRule> cluePoolDistributeRuleMapper;
    @Resource
    private BaseMapper<CustomerPool> customerPoolMapper;
    @Resource
    private ClueService clueService;

    @Override
    public void onApplicationEvent(ExecuteEvent event) {
        try {
            distribute();
        } catch (Exception e) {
            log.error("定时分发线索池异常：{}", e.getMessage());
        }
    }

    public void distribute() {
        log.info("开始分发线索池线索到公海");

        LambdaQueryWrapper<CluePool> poolWrapper = new LambdaQueryWrapper<>();
        poolWrapper.eq(CluePool::getEnable, true).eq(CluePool::getDistribute, true);
        List<CluePool> pools = cluePoolMapper.selectListByLambda(poolWrapper);
        if (CollectionUtils.isEmpty(pools)) {
            log.info("没有启用的自动分发线索池，分发任务结束");
            return;
        }

        List<String> poolIds = pools.stream().map(CluePool::getId).toList();
        LambdaQueryWrapper<CluePoolDistributeRule> ruleWrapper = new LambdaQueryWrapper<>();
        ruleWrapper.in(CluePoolDistributeRule::getCluePoolId, poolIds);
        Map<String, CluePoolDistributeRule> ruleMap = cluePoolDistributeRuleMapper.selectListByLambda(ruleWrapper).stream()
                .collect(Collectors.toMap(CluePoolDistributeRule::getCluePoolId, r -> r, (a, b) -> a));

        for (CluePool pool : pools) {
            CluePoolDistributeRule rule = ruleMap.get(pool.getId());
            if (rule == null || StringUtils.isBlank(rule.getCustomerPoolId())) {
                continue;
            }
            CustomerPool customerPool = customerPoolMapper.selectByPrimaryKey(rule.getCustomerPoolId());
            if (customerPool == null || !Boolean.TRUE.equals(customerPool.getEnable())
                    || !Strings.CS.equals(customerPool.getOrganizationId(), pool.getOrganizationId())) {
                log.warn("线索池 {} 分发目标公海无效，跳过", pool.getId());
                continue;
            }

            List<Clue> clues = queryCluesByRule(pool, rule);
            if (CollectionUtils.isEmpty(clues)) {
                continue;
            }

            for (Clue clue : clues) {
                if (StringUtils.isNotBlank(clue.getTransitionId())
                        && Strings.CS.equals(FormKey.CUSTOMER.name(), clue.getTransitionType())) {
                    continue;
                }
                try {
                    clueService.autoDistributeToCustomerPool(clue, rule.getCustomerPoolId(), pool.getOrganizationId());
                } catch (Exception ex) {
                    log.error("线索自动分发失败 clueId={}", clue.getId(), ex);
                }
            }
        }

        log.info("线索池分发任务完成");
    }

    private List<Clue> queryCluesByRule(CluePool pool, CluePoolDistributeRule rule) {
        CombineSearch combineSearch = parseRuleCombineSearch(rule);
        if (combineSearch == null || CollectionUtils.isEmpty(combineSearch.getConditions())) {
            return List.of();
        }
        Set<String> clueIds = new java.util.LinkedHashSet<>();
        int current = 1;
        final int pageSize = 500;
        while (true) {
            CluePageRequest request = new CluePageRequest();
            request.setCurrent(current);
            request.setPageSize(pageSize);
            request.setPoolId(pool.getId());
            request.setCombineSearch(combineSearch);
            request.setViewId("abc");
            ConditionFilterUtils.parseCondition(request);
            PagerWithOption<List<ClueListResponse>> pageResult =
                    clueService.list(request, InternalUser.ADMIN.getValue(), pool.getOrganizationId(), null, false);
            List<ClueListResponse> rows = pageResult.getList();
            if (CollectionUtils.isEmpty(rows)) {
                break;
            }
            rows.forEach(item -> clueIds.add(item.getId()));
            if (rows.size() < pageSize || current * (long) pageSize >= pageResult.getTotal()) {
                break;
            }
            current++;
        }
        if (CollectionUtils.isEmpty(clueIds)) {
            return List.of();
        }
        return clueMapper.selectByIds(clueIds.toArray(new String[0]));
    }

    private CombineSearch parseRuleCombineSearch(CluePoolDistributeRule rule) {
        if (StringUtils.isBlank(rule.getCondition())) {
            return null;
        }
        String rawCondition = rule.getCondition().trim();
        if (rawCondition.startsWith("[")) {
            List<RuleConditionDTO> oldConditions = JSON.parseArray(rawCondition, RuleConditionDTO.class);
            CombineSearch combineSearch = new CombineSearch();
            combineSearch.setSearchMode(StringUtils.defaultIfBlank(rule.getOperator(), CombineSearch.SearchMode.AND.name()));
            combineSearch.setConditions(oldConditions.stream().map(item -> {
                FilterCondition fc = new FilterCondition();
                fc.setName(item.getColumn());
                fc.setOperator(item.getOperator());
                fc.setValue(item.getValue());
                fc.setType("TIME_RANGE_PICKER");
                fc.setMultipleValue(false);
                return fc;
            }).toList());
            return combineSearch;
        }
        return JSON.parseObject(rawCondition, CombineSearch.class);
    }
}
