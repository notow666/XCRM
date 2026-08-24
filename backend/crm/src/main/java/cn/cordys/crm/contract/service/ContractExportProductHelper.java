package cn.cordys.crm.contract.service;

import cn.cordys.common.domain.BaseModuleFieldValue;
import cn.cordys.common.dto.ExportFieldParam;
import cn.cordys.common.util.JSON;
import cn.cordys.crm.system.dto.field.base.BaseField;
import cn.cordys.crm.system.dto.field.base.SubField;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.Strings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将合同、回款的新产品表数据适配为通用合并导出所需的子表字段值。
 */
final class ContractExportProductHelper {

    private static final String PRODUCTS_BUSINESS_KEY = "products";

    private ContractExportProductHelper() {
    }

    static List<BaseModuleFieldValue> appendProducts(List<BaseModuleFieldValue> moduleFields,
                                                      ExportFieldParam exportFieldParam,
                                                      List<?> products) {
        List<BaseModuleFieldValue> result = moduleFields == null
                ? new ArrayList<>() : new ArrayList<>(moduleFields);
        SubField productField = findProductField(exportFieldParam);
        if (productField == null) {
            return result;
        }

        result.removeIf(fieldValue -> Strings.CS.equals(fieldValue.getFieldId(), productField.getId()));
        result.add(new BaseModuleFieldValue(productField.getId(), buildRows(productField, products)));
        return result;
    }

    private static SubField findProductField(ExportFieldParam exportFieldParam) {
        if (exportFieldParam == null || exportFieldParam.getFormConfig() == null
                || CollectionUtils.isEmpty(exportFieldParam.getFormConfig().getFields())) {
            return null;
        }
        return exportFieldParam.getFormConfig().getFields().stream()
                .filter(SubField.class::isInstance)
                .map(SubField.class::cast)
                .filter(field -> Strings.CS.equals(field.getBusinessKey(), PRODUCTS_BUSINESS_KEY))
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> buildRows(SubField productField, List<?> products) {
        if (CollectionUtils.isEmpty(products)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> rows = new ArrayList<>(products.size());
        for (Object product : products) {
            Map<String, Object> source = JSON.parseObject(JSON.toJSONString(product), Map.class);
            Map<String, Object> row = new LinkedHashMap<>();
            for (BaseField subField : productField.getSubFields()) {
                row.put(subField.getId(), source.get(subField.getBusinessKey()));
            }
            rows.add(row);
        }
        return rows;
    }
}
