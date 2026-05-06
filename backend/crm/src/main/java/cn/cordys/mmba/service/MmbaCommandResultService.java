package cn.cordys.mmba.service;

import cn.cordys.common.uid.IDGenerator;
import cn.cordys.mmba.domain.MmbaCommandResult;
import cn.cordys.mybatis.BaseMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MMBA 指令结果持久化服务。
 * 指令结果回调统一落到 mmba_command_result，并按 reqId + behaviorType + targetValue 做幂等。
 */
@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class MmbaCommandResultService {

    @Resource
    private BaseMapper<MmbaCommandResult> mmbaCommandResultMapper;

    /**
     * 保存或更新统一指令结果。
     */
    public MmbaCommandResult saveOrUpdate(MmbaCommandResult record, String userId) {
        if (StringUtils.isBlank(record.getTargetValue())) {
            log.warn("MMBA结果回执 targetValue 为空 behaviorType={} reqId={} targetType={}",
                    record.getBehaviorType(), record.getReqId(), record.getTargetType());
        }
        MmbaCommandResult query = new MmbaCommandResult();
        query.setBehaviorType(record.getBehaviorType());
        query.setReqId(record.getReqId());
        query.setTargetValue(record.getTargetValue());
        MmbaCommandResult db = mmbaCommandResultMapper.selectOne(query);
        if (db == null) {
            init(record, userId);
            mmbaCommandResultMapper.insert(record);
            log.info("MMBA结果回执新增 behaviorType={} reqId={} targetType={} targetValue={} callbackRecordId={}",
                    record.getBehaviorType(), record.getReqId(), record.getTargetType(), record.getTargetValue(), record.getCallbackRecordId());
            return record;
        }
        record.setId(db.getId());
        record.setCreateTime(db.getCreateTime());
        record.setCreateUser(db.getCreateUser());
        touch(record, userId);
        mmbaCommandResultMapper.update(record);
        log.info("MMBA结果回执更新 behaviorType={} reqId={} targetType={} targetValue={} callbackRecordId={}",
                record.getBehaviorType(), record.getReqId(), record.getTargetType(), record.getTargetValue(), record.getCallbackRecordId());
        return record;
    }

    /**
     * 初始化新增记录的通用审计字段。
     */
    private void init(MmbaCommandResult record, String userId) {
        long now = System.currentTimeMillis();
        record.setId(IDGenerator.nextStr());
        record.setCreateTime(now);
        record.setCreateUser(userId);
        record.setUpdateTime(now);
        record.setUpdateUser(userId);
    }

    /**
     * 更新记录的修改信息。
     */
    private void touch(MmbaCommandResult record, String userId) {
        record.setUpdateTime(System.currentTimeMillis());
        record.setUpdateUser(userId);
    }
}
