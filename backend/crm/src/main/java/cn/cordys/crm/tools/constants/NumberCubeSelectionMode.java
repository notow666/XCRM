package cn.cordys.crm.tools.constants;

public final class NumberCubeSelectionMode {

    public static final String ALL = "ALL";
    public static final String PARTIAL = "PARTIAL";

    private NumberCubeSelectionMode() {
    }

    public static String normalize(String mode) {
        if (ALL.equalsIgnoreCase(mode)) {
            return ALL;
        }
        return PARTIAL;
    }
}
