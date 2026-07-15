package cn.cordys.crm.system.constants;

import cn.cordys.common.constants.FormKey;
import cn.cordys.file.engine.FileSourceModule;
import org.apache.commons.lang3.StringUtils;

/**
 * 表单 key 到文件来源模块段的映射。
 */
public final class FormFileModuleMapper {

    private FormFileModuleMapper() {
    }

    public static String toModule(String formKey) {
        if (StringUtils.isBlank(formKey)) {
            return FileSourceModule.SYSTEM;
        }
        if (FormKey.CLUE.getKey().equals(formKey)) {
            return FileSourceModule.CLUE;
        }
        if (FormKey.CUSTOMER.getKey().equals(formKey) || FormKey.CONTACT.getKey().equals(formKey)) {
            return FileSourceModule.CUSTOMER;
        }
        if (FormKey.OPPORTUNITY.getKey().equals(formKey) || FormKey.QUOTATION.getKey().equals(formKey)) {
            return FileSourceModule.BUSINESS;
        }
        if (FormKey.PRODUCT.getKey().equals(formKey) || FormKey.PRICE.getKey().equals(formKey)) {
            return FileSourceModule.PRODUCT;
        }
        if (FormKey.CONTRACT.getKey().equals(formKey)
                || FormKey.INVOICE.getKey().equals(formKey)
                || FormKey.CONTRACT_PAYMENT_PLAN.getKey().equals(formKey)
                || FormKey.CONTRACT_PAYMENT_RECORD.getKey().equals(formKey)) {
            return FileSourceModule.CONTRACT;
        }
        return FileSourceModule.SYSTEM;
    }
}
