package cn.cordys.crm.clue.domain;

import cn.cordys.common.domain.BaseModuleFieldValue;
import cn.cordys.common.dto.condition.CombineSearch;
import cn.cordys.common.dto.condition.FilterCondition;
import cn.cordys.common.domain.BaseModel;
import cn.cordys.common.util.BeanUtils;
import cn.cordys.common.util.JSON;
import cn.cordys.crm.system.constants.RecycleConditionColumnKey;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Table;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.util.List;
import java.util.Objects;

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
        return matchDistributeCondition(clue, List.of());
    }

    public boolean matchDistributeCondition(Clue clue, List<BaseModuleFieldValue> moduleFields) {
        if (clue == null || StringUtils.isBlank(condition)) {
            return false;
        }
        String rawCondition = condition.trim();
        try {
            return matchCombineSearchCondition(clue, moduleFields, rawCondition);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean matchCombineSearchCondition(Clue clue, List<BaseModuleFieldValue> moduleFields, String rawCondition) {
        CombineSearch combineSearch = JSON.parseObject(rawCondition, CombineSearch.class);
        if (combineSearch == null) {
            return false;
        }
        // 条件为空：与列表「无高级筛选」一致，视为全部线索满足分发规则
        if (CollectionUtils.isEmpty(combineSearch.getConditions())) {
            return true;
        }
        boolean allMatch = Strings.CS.equals(CombineSearch.SearchMode.AND.name(), combineSearch.getSearchMode());
        if (allMatch) {
            return combineSearch.getConditions().stream().allMatch(item -> matchFilterCondition(clue, moduleFields, item));
        }
        return combineSearch.getConditions().stream().anyMatch(item -> matchFilterCondition(clue, moduleFields, item));
    }

    private boolean matchFilterCondition(Clue clue, List<BaseModuleFieldValue> moduleFields, FilterCondition filterCondition) {
        String combineOperator = filterCondition.getCombineOperator();
        if (Strings.CS.equalsAny(combineOperator, FilterCondition.CombineConditionOperator.EMPTY.name(),
                FilterCondition.CombineConditionOperator.NOT_EMPTY.name())) {
            return Strings.CS.equals(combineOperator, FilterCondition.CombineConditionOperator.EMPTY.name())
                    ? isEmptyValue(resolveConditionValue(clue, moduleFields, filterCondition.getName()))
                    : !isEmptyValue(resolveConditionValue(clue, moduleFields, filterCondition.getName()));
        }
        Object sourceValue = resolveConditionValue(clue, moduleFields, filterCondition.getName());
        if (sourceValue == null) {
            return false;
        }
        Object combineValue = filterCondition.getCombineValue();
        return switch (FilterCondition.CombineConditionOperator.valueOf(combineOperator)) {
            case GT -> compareNumber(sourceValue, combineValue) > 0;
            case LT -> compareNumber(sourceValue, combineValue) < 0;
            case GE -> compareNumber(sourceValue, combineValue) >= 0;
            case LE -> compareNumber(sourceValue, combineValue) <= 0;
            case EQUALS -> valueEquals(sourceValue, combineValue);
            case NOT_EQUALS -> !valueEquals(sourceValue, combineValue);
            case IN -> containsValue(sourceValue, combineValue);
            case NOT_IN -> !containsValue(sourceValue, combineValue);
            case BETWEEN -> matchBetween(sourceValue, combineValue);
            case CONTAINS -> containsTextOrElement(sourceValue, combineValue);
            case NOT_CONTAINS -> !containsTextOrElement(sourceValue, combineValue);
            default -> false;
        };
    }

    private Object resolveConditionValue(Clue clue, List<BaseModuleFieldValue> moduleFields, String conditionName) {
        if (Strings.CS.equalsAny(conditionName, RecycleConditionColumnKey.STORAGE_TIME, RecycleConditionColumnKey.CREATE_TIME)) {
            return clue.getCreateTime();
        }
        if (Strings.CS.equals(conditionName, "followTime")) {
            return clue.getFollowTime();
        }
        Object businessValue = BeanUtils.getFieldValueByName(conditionName, clue);
        if (businessValue != null) {
            return businessValue;
        }
        if (CollectionUtils.isEmpty(moduleFields)) {
            return null;
        }
        return moduleFields.stream()
                .filter(item -> Strings.CS.equals(item.getFieldId(), conditionName))
                .map(BaseModuleFieldValue::getFieldValue)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private boolean isEmptyValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String strValue) {
            return StringUtils.isBlank(strValue);
        }
        if (value instanceof List<?> values) {
            return values.isEmpty();
        }
        return false;
    }

    private int compareNumber(Object sourceValue, Object targetValue) {
        Long sourceNum = parseLong(sourceValue);
        Long targetNum = parseLong(targetValue);
        if (sourceNum == null || targetNum == null) {
            return Integer.MIN_VALUE;
        }
        return Long.compare(sourceNum, targetNum);
    }

    private boolean matchBetween(Object sourceValue, Object targetValue) {
        if (!(targetValue instanceof List<?> values) || values.size() < 2) {
            return false;
        }
        Long sourceNum = parseLong(sourceValue);
        Long start = parseLong(values.get(0));
        Long end = parseLong(values.get(1));
        if (sourceNum == null || start == null || end == null) {
            return false;
        }
        return sourceNum >= start && sourceNum <= end;
    }

    private boolean containsValue(Object sourceValue, Object targetValue) {
        if (sourceValue instanceof List<?> sourceList) {
            if (targetValue instanceof List<?> targetList) {
                return sourceList.stream().anyMatch(sourceItem ->
                        targetList.stream().anyMatch(targetItem -> valueEquals(sourceItem, targetItem)));
            }
            return sourceList.stream().anyMatch(sourceItem -> valueEquals(sourceItem, targetValue));
        }
        if (targetValue instanceof List<?> targetList) {
            return targetList.stream().anyMatch(targetItem -> valueEquals(sourceValue, targetItem));
        }
        return valueEquals(sourceValue, targetValue);
    }

    private boolean containsTextOrElement(Object sourceValue, Object targetValue) {
        if (sourceValue instanceof List<?> sourceList) {
            return sourceList.stream().anyMatch(item -> valueEquals(item, targetValue));
        }
        return sourceValue.toString().contains(targetValue.toString());
    }

    private boolean valueEquals(Object sourceValue, Object targetValue) {
        if (sourceValue instanceof List<?> sourceList) {
            if (targetValue instanceof List<?> targetList) {
                return sourceList.size() == targetList.size() && sourceList.stream().allMatch(source ->
                        targetList.stream().anyMatch(target -> Strings.CS.equals(String.valueOf(source), String.valueOf(target))));
            }
            return sourceList.stream().anyMatch(source -> Strings.CS.equals(String.valueOf(source), String.valueOf(targetValue)));
        }
        if (targetValue instanceof List<?> targetList) {
            return targetList.stream().anyMatch(target -> Strings.CS.equals(String.valueOf(sourceValue), String.valueOf(target)));
        }
        return Strings.CS.equals(String.valueOf(sourceValue), String.valueOf(targetValue));
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
