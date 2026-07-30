package cn.cordys.crm.customer.constants;

import java.util.Set;

public final class CustomerContractDeletePolicyType {

    public static final String CASCADE = "CASCADE";
    public static final String KEEP_CONTRACT = "KEEP_CONTRACT";

    private static final Set<String> VALUES = Set.of(CASCADE, KEEP_CONTRACT);

    public static boolean isValid(String policy) {
        return VALUES.contains(policy);
    }

    private CustomerContractDeletePolicyType() {
    }
}
