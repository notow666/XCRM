package cn.cordys.mmba.service;

import cn.cordys.common.uid.IDGenerator;
import cn.cordys.mmba.domain.MmbaRequestRecord;
import cn.cordys.mybatis.BaseMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * MMBA 请求流水服务。
 * 发送侧每次调用 MMBA 都会先初始化一条请求记录，再按同步结果回填状态。
 * 请求流水本质上属于接入审计日志，因此这里使用独立事务提交，避免主流程异常时把流水一并回滚。
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class MmbaRequestRecordService {

    @Resource
    private BaseMapper<MmbaRequestRecord> mmbaRequestRecordMapper;

    /**
     * 初始化请求记录。
     * 使用独立事务，确保即使后续获取 token 或调用 MMBA 失败，请求流水也能保留下来。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public MmbaRequestRecord init(MmbaRequestRecord record, String userId) {
        long now = System.currentTimeMillis();
        record.setId(IDGenerator.nextStr());
        record.setCreateTime(now);
        record.setCreateUser(userId);
        record.setUpdateTime(now);
        record.setUpdateUser(userId);
        mmbaRequestRecordMapper.insert(record);
        return record;
    }

    /**
     * 回填请求记录的执行结果。
     * 同样使用独立事务，保证失败状态、错误信息和原始返回不会因上层异常再次丢失。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void update(MmbaRequestRecord record, String userId) {
        record.setUpdateTime(System.currentTimeMillis());
        record.setUpdateUser(userId);
        mmbaRequestRecordMapper.update(record);
    }

//    public MmbaRequestRecord findByReqId(String reqId) {
//        if (reqId == null || reqId.isBlank()) {
//            return null;
//        }
//        MmbaRequestRecord query = new MmbaRequestRecord();
//        query.setReqId(reqId);
//        return mmbaRequestRecordMapper.selectOne(query);
//    }
}
