package cn.cordys.mmba.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class MmbaCallLogCleanConfigSaveRequest {
    @NotNull
    private Boolean enable;
    private List<String> userIds;
}
