package cn.cordys.platform.domain;

import cn.cordys.common.domain.BaseModel;
import jakarta.persistence.Table;
import lombok.Data;

import java.io.Serial;

@Data
@Table(name = "platform_user")
public class PlatformUser extends BaseModel {
    @Serial
    private static final long serialVersionUID = 419084329307808640L;
    private String username;
    private String passwordHash;
    private String status;
}
