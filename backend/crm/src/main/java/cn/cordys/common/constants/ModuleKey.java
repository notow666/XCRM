package cn.cordys.common.constants;

import lombok.Getter;

@Getter
public enum ModuleKey {

    /**
     * 首页
     */
    HOME("home", true, 1),
    /**
     * 线索管理
     */
    CLUE("clue", true, 2),
    /**
     * 客户管理
     */
    CUSTOMER("customer", true, 3),
    /**
     * 商机管理
     */
    BUSINESS("business", false, 5),
    /**
     * 产品管理
     */
    PRODUCT("product", false, 6),
    /**
     * 系统设置
     */
    SETTING("setting", true, 99);

    private final String key;

    private final boolean enable;

    private final long pos;

    ModuleKey(String key, boolean enable, long pos) {
        this.key = key;
        this.enable = enable;
        this.pos = pos;
    }
}
