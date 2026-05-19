package cn.cordys.crm.customer.dto;

import lombok.Data;

/**
 * 手机号冲突DTO
 * 用于公海导入预检查时标识手机号在数据库中的冲突类型
 */
@Data
public class MobileConflictDTO {
    /**
     * 手机号
     */
    private String mobile;
    
    /**
     * 冲突类型
     * OTHER_POOL: 其他公海池冲突（in_shared_pool=true 且 pool_id!=目标公海池）
     * POOL_SOURCE_PRIVATE: 公海来源客户冲突（in_shared_pool=false 且 create_source=POOL_IMPORT）
     */
    private String conflictType;
}
