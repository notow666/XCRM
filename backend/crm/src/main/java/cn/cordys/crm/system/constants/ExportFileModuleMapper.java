package cn.cordys.crm.system.constants;

import cn.cordys.file.engine.FileSourceModule;
import org.apache.commons.lang3.StringUtils;

/**
 * 导出类型到文件来源模块段的映射。
 */
public final class ExportFileModuleMapper {

    private ExportFileModuleMapper() {
    }

    public static String toModule(String exportType) {
        if (StringUtils.isBlank(exportType)) {
            return FileSourceModule.SYSTEM;
        }
        ExportConstants.ExportType type;
        try {
            type = ExportConstants.ExportType.valueOf(exportType);
        } catch (IllegalArgumentException e) {
            return FileSourceModule.SYSTEM;
        }
        return switch (type) {
            case CUSTOMER, CUSTOMER_POOL, CUSTOMER_CONTACT, CUSTOMER_POOL_IMPORT -> FileSourceModule.CUSTOMER;
            case CLUE, CLUE_POOL -> FileSourceModule.CLUE;
            case OPPORTUNITY, BUSINESS_TITLE -> FileSourceModule.BUSINESS;
            case PRODUCT_PRICE -> FileSourceModule.PRODUCT;
            case CONTRACT, CONTRACT_PAYMENT_PLAN, CONTRACT_INVOICE, CONTRACT_PAYMENT_RECORD -> FileSourceModule.CONTRACT;
            case NUMBER_CUBE -> FileSourceModule.CUBE;
        };
    }
}
