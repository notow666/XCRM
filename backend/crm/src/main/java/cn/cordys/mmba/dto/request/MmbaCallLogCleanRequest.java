package cn.cordys.mmba.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class MmbaCallLogCleanRequest {
    @NotEmpty
    private List<String> userIds;
}
