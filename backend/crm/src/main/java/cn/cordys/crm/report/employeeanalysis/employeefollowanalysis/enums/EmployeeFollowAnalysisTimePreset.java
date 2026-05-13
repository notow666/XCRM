package cn.cordys.crm.report.employeeanalysis.employeefollowanalysis.enums;

import org.apache.commons.lang3.StringUtils;

public enum EmployeeFollowAnalysisTimePreset {
    TODAY("today"),
    YESTERDAY("yesterday"),
    WEEK("week"),
    MONTH("month"),
    CUSTOM("custom");

    private final String value;

    EmployeeFollowAnalysisTimePreset(String value) {
        this.value = value;
    }

    public static EmployeeFollowAnalysisTimePreset fromValue(String value) {
        for (EmployeeFollowAnalysisTimePreset preset : values()) {
            if (StringUtils.equals(preset.value, value)) {
                return preset;
            }
        }
        throw new IllegalArgumentException("unsupported timePreset: " + value);
    }
}
