package cn.cordys.crm.system.constants;

import cn.cordys.common.constants.ModuleKey;

import java.util.Set;

public class ModuleConstants {
    public final static Set<String> DISPLAY_MODULE = Set.of(
            ModuleKey.HOME.getKey(),
            ModuleKey.CLUE.getKey(),
            ModuleKey.CUSTOMER.getKey(),
            "task",
            "contract",
            "report",
            "mmbaAudit",
            ModuleKey.SETTING.getKey()
    );
}
