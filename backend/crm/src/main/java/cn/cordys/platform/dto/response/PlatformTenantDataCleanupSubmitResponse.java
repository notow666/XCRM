package cn.cordys.platform.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class PlatformTenantDataCleanupSubmitResponse {

    private List<PlatformTenantDataCleanupTaskResponse> tasks;
}
