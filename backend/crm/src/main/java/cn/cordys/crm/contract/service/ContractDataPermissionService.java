package cn.cordys.crm.contract.service;

import cn.cordys.common.exception.GenericException;
import cn.cordys.common.response.result.CrmHttpResultCode;
import cn.cordys.common.service.DataScopeService;
import cn.cordys.crm.contract.domain.Contract;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.stereotype.Service;

@Service
public class ContractDataPermissionService {

    @Resource
    private DataScopeService dataScopeService;

    public void checkDataPermission(String userId, String orgId, Contract contract, String permission) {
        boolean ownerAllowed = StringUtils.isNotBlank(contract.getOwner())
                && dataScopeService.hasDataPermission(userId, orgId, contract.getOwner(), permission);
        boolean signerAllowed = StringUtils.isNotBlank(contract.getSignerId())
                && !Strings.CS.equals(contract.getOwner(), contract.getSignerId())
                && dataScopeService.hasDataPermission(userId, orgId, contract.getSignerId(), permission);
        if (!ownerAllowed && !signerAllowed) {
            throw new GenericException(CrmHttpResultCode.FORBIDDEN);
        }
    }
}
