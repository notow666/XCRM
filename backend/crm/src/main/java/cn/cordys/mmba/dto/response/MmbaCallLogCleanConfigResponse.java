package cn.cordys.mmba.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class MmbaCallLogCleanConfigResponse {
    private Boolean enable;
    private List<String> userIds;
}
