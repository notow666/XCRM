package cn.cordys.crm.clue.domain;

import cn.cordys.common.dto.condition.CombineSearch;
import cn.cordys.common.dto.condition.FilterCondition;
import cn.cordys.common.domain.BaseModel;
import cn.cordys.common.util.JSON;
import cn.cordys.common.utils.RecycleConditionUtils;
import cn.cordys.crm.system.constants.RecycleConditionColumnKey;
import cn.cordys.crm.system.constants.RecycleConditionScopeKey;
import cn.cordys.crm.system.dto.RuleConditionDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Table;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.util.Collections;
import java.util.List;

@Data
@Table(name = "clue_pool_distribute_rule")
public class CluePoolDistributeRule extends BaseModel {

    @Schema(description = "线索池ID")
    private String cluePoolId;

    @Schema(description = "公海池ID")
    private String customerPoolId;

    @Schema(description = "操作符")
    private String operator;

    @Schema(description = "分发条件")
    private String condition;

    /**
     * 校验线索是否满足当前分发规则
     *
     * @param clue 线索
     *
     * @return true-满足分发条件，false-不满足
     */
    public boolean matchDistributeCondition(Clue clue) {
        if (clue == null || StringUtils.isBlank(condition)) {
            return false;
        }
        String rawCondition = condition.trim();
        try {
            if (rawCondition.startsWith("[")) {
                return matchLegacyCondition(clue, rawCondition);
            }
            return matchCombineSearchCondition(clue, rawCondition);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean matchLegacyCondition(Clue clue, String rawCondition) {
        List<RuleConditionDTO> conditions = JSON.parseArray(rawCondition, RuleConditionDTO.class);
        if (CollectionUtils.isEmpty(conditions)) {
            return false;
        }
        boolean allMatch = Strings.CS.equals(CombineSearch.SearchMode.AND.name(), operator);
        if (allMatch) {
            return conditions.stream().allMatch(item -> matchLegacyTimeCondition(item, clue));
        }
        return conditions.stream().anyMatch(item -> matchLegacyTimeCondition(item, clue));
    }

    private boolean matchLegacyTimeCondition(RuleConditionDTO conditionItem, Clue clue) {
        List<String> scope = conditionItem.getScope() != null ? conditionItem.getScope() : Collections.emptyList();
        if (Strings.CS.equals(conditionItem.getColumn(), RecycleConditionColumnKey.STORAGE_TIME)) {
            if (scope.contains(RecycleConditionScopeKey.CREATED)) {
                return RecycleConditionUtils.matchTime(conditionItem, clue.getCreateTime());
            }
            if (scope.contains(RecycleConditionScopeKey.PICKED)) {
                return RecycleConditionUtils.matchTime(conditionItem, clue.getCollectionTime());
            }
            return RecycleConditionUtils.matchTime(conditionItem, clue.getCreateTime())
                    || RecycleConditionUtils.matchTime(conditionItem, clue.getCollectionTime());
        }
        return RecycleConditionUtils.matchTime(conditionItem, clue.getFollowTime());
    }

    private boolean matchCombineSearchCondition(Clue clue, String rawCondition) {
        CombineSearch combineSearch = JSON.parseObject(rawCondition, CombineSearch.class);
        if (combineSearch == null || CollectionUtils.isEmpty(combineSearch.getConditions())) {
            return false;
        }
        boolean allMatch = Strings.CS.equals(CombineSearch.SearchMode.AND.name(), combineSearch.getSearchMode());
        if (allMatch) {
            return combineSearch.getConditions().stream().allMatch(item -> matchFilterCondition(clue, item));
        }
        return combineSearch.getConditions().stream().anyMatch(item -> matchFilterCondition(clue, item));
    }

    private boolean matchFilterCondition(Clue clue, FilterCondition filterCondition) {
        Long sourceTime = Strings.CS.equals(filterCondition.getName(), RecycleConditionColumnKey.STORAGE_TIME)
                ? clue.getCreateTime()
                : clue.getFollowTime();
        String combineOperator = filterCondition.getCombineOperator();
        if (Strings.CS.equalsAny(combineOperator, FilterCondition.CombineConditionOperator.EMPTY.name(),
                FilterCondition.CombineConditionOperator.NOT_EMPTY.name())) {
            return Strings.CS.equals(combineOperator, FilterCondition.CombineConditionOperator.EMPTY.name())
                    ? sourceTime == null
                    : sourceTime != null;
        }
        if (sourceTime == null) {
            return false;
        }
        Object combineValue = filterCondition.getCombineValue();
        if (combineValue instanceof List<?> values && values.size() >= 2) {
            long start = Long.parseLong(values.get(0).toString());
            long end = Long.parseLong(values.get(1).toString());
            return sourceTime >= start && sourceTime <= end;
        }
        if (combineValue == null) {
            return false;
        }
        long target = Long.parseLong(combineValue.toString());
        if (Strings.CS.equals(combineOperator, FilterCondition.CombineConditionOperator.GT.name())) {
            return sourceTime > target;
        }
        if (Strings.CS.equals(combineOperator, FilterCondition.CombineConditionOperator.LT.name())) {
            return sourceTime < target;
        }
        if (Strings.CS.equals(combineOperator, FilterCondition.CombineConditionOperator.GE.name())) {
            return sourceTime >= target;
        }
        if (Strings.CS.equals(combineOperator, FilterCondition.CombineConditionOperator.LE.name())) {
            return sourceTime <= target;
        }
        if (Strings.CS.equals(combineOperator, FilterCondition.CombineConditionOperator.EQUALS.name())) {
            return sourceTime.equals(target);
        }
        if (Strings.CS.equals(combineOperator, FilterCondition.CombineConditionOperator.NOT_EQUALS.name())) {
            return !sourceTime.equals(target);
        }
        return false;
    }
}
