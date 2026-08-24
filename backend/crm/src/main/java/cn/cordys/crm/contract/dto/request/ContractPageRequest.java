package cn.cordys.crm.contract.dto.request;

import cn.cordys.common.dto.BasePageRequest;
import lombok.Data;


@Data
public class ContractPageRequest extends BasePageRequest {

    private String sourceFormKey;

    public String getCustomerId() {
        return null;
    }
}
