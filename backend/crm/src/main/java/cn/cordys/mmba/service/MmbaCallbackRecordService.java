package cn.cordys.mmba.service;

import cn.cordys.common.uid.IDGenerator;
import cn.cordys.mmba.domain.MmbaCallbackRecord;
import cn.cordys.mmba.mapper.ExtMmbaCallbackRecordMapper;
import cn.cordys.mybatis.BaseMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class MmbaCallbackRecordService {

    @Resource
    private BaseMapper<MmbaCallbackRecord> mmbaCallbackRecordMapper;
    @Resource
    private ExtMmbaCallbackRecordMapper extMmbaCallbackRecordMapper;

    public MmbaCallbackRecord init(MmbaCallbackRecord record, String userId) {
        long now = System.currentTimeMillis();
        record.setId(IDGenerator.nextStr());
        record.setCreateTime(now);
        record.setCreateUser(userId);
        record.setUpdateTime(now);
        record.setUpdateUser(userId);
        mmbaCallbackRecordMapper.insert(record);
        return record;
    }

    public void update(MmbaCallbackRecord record, String userId) {
        record.setUpdateTime(System.currentTimeMillis());
        record.setUpdateUser(userId);
        mmbaCallbackRecordMapper.update(record);
    }

    public void markSuccessAndClearPayload(MmbaCallbackRecord record, String userId) {
        record.setUpdateTime(System.currentTimeMillis());
        record.setUpdateUser(userId);
        extMmbaCallbackRecordMapper.markSuccessAndClearPayload(record);
    }

    public MmbaCallbackRecord findLatestByPayloadHash(String payloadHash) {
        MmbaCallbackRecord query = new MmbaCallbackRecord();
        query.setPayloadHash(payloadHash);
        List<MmbaCallbackRecord> records = mmbaCallbackRecordMapper.select(query);
        return records == null || records.isEmpty() ? null : records.get(0);
    }

    public List<MmbaCallbackRecord> listRetryableFailedRecords(int limit, int maxRetryCount) {
        if (limit <= 0) {
            return List.of();
        }
        MmbaCallbackRecord query = new MmbaCallbackRecord();
        query.setProcessStatus("FAILED");
        List<MmbaCallbackRecord> records = mmbaCallbackRecordMapper.select(query);
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        return records.stream()
                .filter(record -> record != null)
                .filter(record -> maxRetryCount <= 0 || record.getRetryCount() == null || record.getRetryCount() < maxRetryCount)
                .sorted(Comparator.comparing(MmbaCallbackRecord::getUpdateTime, Comparator.nullsLast(Long::compareTo))
                        .thenComparing(MmbaCallbackRecord::getCreateTime, Comparator.nullsLast(Long::compareTo)))
                .limit(limit)
                .collect(Collectors.toList());
    }
}
