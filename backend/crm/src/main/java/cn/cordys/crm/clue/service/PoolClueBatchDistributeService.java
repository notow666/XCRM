package cn.cordys.crm.clue.service;

import cn.cordys.common.constants.FormKey;
import cn.cordys.crm.clue.domain.Clue;
import cn.cordys.crm.customer.service.PoolCustomerService;
import cn.cordys.crm.system.dto.request.PoolBatchDistributeRequest;
import cn.cordys.mybatis.BaseMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class PoolClueBatchDistributeService {

    private static final int BATCH_SIZE = 200;

    @Resource
    private BaseMapper<Clue> clueMapper;
    @Resource
    private ClueService clueService;
    @Resource
    private PoolCustomerService poolCustomerService;

    /**
     * 批量分发（异步执行）：不校验分发规则，直接按目标公海池尝试分发。
     */
    @Async("threadPoolTaskExecutor")
    public void batchDistribute(PoolBatchDistributeRequest request, String orgId) {
        try {
            List<Clue> clues = clueMapper.selectByIds(request.getBatchIds().toArray(new String[0]));
            List<Clue> candidates = clues.stream()
                    .filter(clue -> clue != null && Boolean.TRUE.equals(clue.getInSharedPool()))
                    .filter(clue -> StringUtils.isBlank(clue.getTransitionId())
                            || !Strings.CS.equals(FormKey.CUSTOMER.name(), clue.getTransitionType()))
                    .toList();
            if (candidates.isEmpty()) {
                return;
            }
            Set<String> allowedPhones = poolCustomerService.batchPreCheckClueMoveToPool(
                    candidates.stream().map(Clue::getPhone).toList(),
                    request.getCustomerPoolId(),
                    orgId
            );
            if (allowedPhones.isEmpty()) {
                return;
            }

            List<String> successIds = new ArrayList<>();
            for (Clue clue : candidates) {
                if (!allowedPhones.contains(clue.getPhone())) {
                    continue;
                }
                if (clueService.autoDistributeToCustomerPool(clue, request.getCustomerPoolId(), orgId)) {
                    successIds.add(clue.getId());
                    if (successIds.size() >= BATCH_SIZE) {
                        clueService.silenceBatchDelete(successIds);
                        successIds.clear();
                    }
                }
            }
            clueService.silenceBatchDelete(successIds);
        } catch (Exception ex) {
            log.error("批量分发线索到公海池失败, customerPoolId={}", request.getCustomerPoolId(), ex);
        }
    }
}
