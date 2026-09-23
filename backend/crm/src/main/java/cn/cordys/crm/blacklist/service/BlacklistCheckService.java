package cn.cordys.crm.blacklist.service;

import cn.cordys.common.exception.GenericException;
import cn.cordys.context.TenantContext;
import cn.cordys.crm.blacklist.domain.Blacklist;
import cn.cordys.crm.blacklist.mapper.BlacklistMapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class BlacklistCheckService {
    public static final String ADD_MESSAGE = "此手机号码属于黑名单号码，无法添加。";
    public static final String IMPORT_MESSAGE = "此手机号码属于黑名单号码，无法导入。";
    public static final String OPERATE_MESSAGE = "此手机号码属于黑名单号码，无法操作。";
    @Resource private BlacklistMapper mapper;
    @Resource private PlatformTransactionManager transactionManager;

    /** 普通批次事务：主数据与关联数据一起提交，不显式加锁或串行化租户操作。 */
    public <T> T write(Supplier<T> action) {
        TenantContext.requireTenantId();
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }

    public Set<String> find(Collection<String> mobiles) {
        TenantContext.requireTenantId();
        List<String> values = mobiles.stream().filter(StringUtils::isNotBlank).map(StringUtils::deleteWhitespace).distinct().sorted().toList();
        Set<String> result = new HashSet<>();
        for (int i = 0; i < values.size(); i += 500) {
            result.addAll(mapper.find(values.subList(i, Math.min(i + 500, values.size()))).stream()
                    .map(Blacklist::getMobile).collect(Collectors.toSet()));
        }
        return result;
    }

    public void validateSave(String mobile, boolean adding) {
        if (StringUtils.isNotBlank(mobile) && !find(List.of(mobile)).isEmpty()) {
            throw new GenericException(adding ? ADD_MESSAGE : "此手机号码属于黑名单号码，无法修改。");
        }
    }

    public void validateCommunication(String mobile) {
        if (StringUtils.isBlank(mobile)) {
            return;
        }
        String target = StringUtils.deleteWhitespace(mobile).replace("-", "");
        // 通信接口的国家码不能绕过本地11位号码的限制；不改变黑名单录入格式规则。
        if (target.startsWith("+86") && target.length() == 14) target = target.substring(3);
        if (target.startsWith("0086") && target.length() == 15) target = target.substring(4);
        if (!find(List.of(target)).isEmpty()) {
            throw new GenericException(OPERATE_MESSAGE);
        }
    }
}
