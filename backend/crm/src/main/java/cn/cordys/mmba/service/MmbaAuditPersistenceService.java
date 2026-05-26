package cn.cordys.mmba.service;

import cn.cordys.common.constants.CrmLoggers;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.mmba.domain.MmbaCallRecordAudit;
import cn.cordys.mmba.domain.MmbaDeviceInfoAudit;
import cn.cordys.mmba.domain.MmbaSmsRecordAudit;
import cn.cordys.mmba.domain.MmbaWxAccountAudit;
import cn.cordys.mmba.domain.MmbaWxChatAudit;
import cn.cordys.mmba.domain.MmbaWxFriendChangeAudit;
import cn.cordys.mmba.domain.MmbaWxFriendListAudit;
import cn.cordys.mmba.domain.MmbaWxLoginAudit;
import cn.cordys.mmba.mapper.ExtMmbaAuditMapper;
import cn.cordys.mybatis.BaseMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MMBA 审计分表持久化服务。
 * 新结构下：
 * 1. call/sms/wx* 这几张表直接按 esId 做主键和幂等；
 * 2. deviceInfo/deviceStatus/wxLogin 保留本地 id，但不再依赖 BaseModel 或 organization_id。
 */
@Slf4j(topic = CrmLoggers.MMBA_CALLBACK)
@Service
@Transactional(rollbackFor = Exception.class)
public class MmbaAuditPersistenceService {

    public static final class SaveOrUpdateResult<T> {
        private final T previous;
        private final T current;
        private final boolean inserted;

        public SaveOrUpdateResult(T previous, T current, boolean inserted) {
            this.previous = previous;
            this.current = current;
            this.inserted = inserted;
        }

        public T getPrevious() {
            return previous;
        }

        public T getCurrent() {
            return current;
        }

        public boolean isInserted() {
            return inserted;
        }
    }

    @Resource
    private BaseMapper<MmbaCallRecordAudit> mmbaCallRecordAuditMapper;
    @Resource
    private BaseMapper<MmbaSmsRecordAudit> mmbaSmsRecordAuditMapper;
    @Resource
    private BaseMapper<MmbaWxAccountAudit> mmbaWxAccountAuditMapper;
    @Resource
    private BaseMapper<MmbaWxChatAudit> mmbaWxChatAuditMapper;
    @Resource
    private BaseMapper<MmbaWxFriendChangeAudit> mmbaWxFriendChangeAuditMapper;
    @Resource
    private BaseMapper<MmbaWxFriendListAudit> mmbaWxFriendListAuditMapper;
    @Resource
    private BaseMapper<MmbaWxLoginAudit> mmbaWxLoginAuditMapper;
    @Resource
    private ExtMmbaAuditMapper extMmbaAuditMapper;

    public MmbaCallRecordAudit saveOrUpdateCallAudit(MmbaCallRecordAudit record, String userId) {
        extMmbaAuditMapper.upsertCallRecordAudit(record);
        return record;
    }

    public SaveOrUpdateResult<MmbaCallRecordAudit> saveOrUpdateCallAuditWithResult(MmbaCallRecordAudit record, String userId) {
        MmbaCallRecordAudit previous = loadByEsId(record.getEsId(), mmbaCallRecordAuditMapper);
        extMmbaAuditMapper.upsertCallRecordAudit(record);
        return new SaveOrUpdateResult<>(previous, record, previous == null);
    }

    public MmbaCallRecordAudit findCallAuditByEsId(String esId) {
        return esId == null ? null : mmbaCallRecordAuditMapper.selectByPrimaryKey(esId);
    }

    public MmbaSmsRecordAudit saveOrUpdateSmsAudit(MmbaSmsRecordAudit record, String userId) {
        extMmbaAuditMapper.upsertSmsRecordAudit(record);
        return record;
    }

    public SaveOrUpdateResult<MmbaSmsRecordAudit> saveOrUpdateSmsAuditWithResult(MmbaSmsRecordAudit record, String userId) {
        MmbaSmsRecordAudit previous = loadByEsId(record.getEsId(), mmbaSmsRecordAuditMapper);
        extMmbaAuditMapper.upsertSmsRecordAudit(record);
        return new SaveOrUpdateResult<>(previous, record, previous == null);
    }

    public MmbaSmsRecordAudit findSmsAuditByEsId(String esId) {
        return esId == null ? null : mmbaSmsRecordAuditMapper.selectByPrimaryKey(esId);
    }

    public MmbaWxChatAudit findWxChatAuditByEsId(String esId) {
        return esId == null ? null : mmbaWxChatAuditMapper.selectByPrimaryKey(esId);
    }

    public MmbaWxAccountAudit saveOrUpdateWxAccountAudit(MmbaWxAccountAudit record, String userId) {
        extMmbaAuditMapper.upsertWxAccountAudit(record);
        return record;
    }

    public MmbaWxChatAudit saveOrUpdateWxChatAudit(MmbaWxChatAudit record, String userId) {
        extMmbaAuditMapper.upsertWxChatAudit(record);
        return record;
    }

    public SaveOrUpdateResult<MmbaWxChatAudit> saveOrUpdateWxChatAuditWithResult(MmbaWxChatAudit record, String userId) {
        // 这里只保留一次旧值查询，供自动跟进判断“是否首次进入成功态”使用；
        // 真正的写入路径已经固定为原子 upsert，不再依赖查询结果决定 insert / update。
        MmbaWxChatAudit previous = loadByEsId(record.getEsId(), mmbaWxChatAuditMapper);
        extMmbaAuditMapper.upsertWxChatAudit(record);
        return new SaveOrUpdateResult<>(previous, record, previous == null);
    }

    public MmbaWxFriendChangeAudit saveOrUpdateWxFriendChangeAudit(MmbaWxFriendChangeAudit record, String userId) {
        extMmbaAuditMapper.upsertWxFriendChangeAudit(record);
        return record;
    }

    public MmbaWxFriendListAudit saveOrUpdateWxFriendListAudit(MmbaWxFriendListAudit record, String userId) {
        return saveOrReplaceByEsId(record, mmbaWxFriendListAuditMapper);
    }

    public MmbaWxLoginAudit saveOrUpdateWxLoginAudit(MmbaWxLoginAudit record, String userId) {
        MmbaWxLoginAudit query = new MmbaWxLoginAudit();
        query.setAppPkgName(record.getAppPkgName());
        query.setStaffIdInApp(record.getStaffIdInApp());
        query.setCreateTime(record.getCreateTime());
        MmbaWxLoginAudit db = mmbaWxLoginAuditMapper.selectOne(query);
        if (db == null) {
            record.setId(IDGenerator.nextStr());
            mmbaWxLoginAuditMapper.insert(record);
            return record;
        }
        record.setId(db.getId());
        mmbaWxLoginAuditMapper.update(record);
        return record;
    }

    public MmbaDeviceInfoAudit saveOrUpdateDeviceInfoAudit(MmbaDeviceInfoAudit record, String userId) {
        record.setId(IDGenerator.nextStr());
        extMmbaAuditMapper.upsertDeviceInfoAudit(record);
        return record;
    }

    private <T> T saveOrReplaceByEsId(T record, BaseMapper<T> mapper) {
        return saveOrReplaceByEsIdWithResult(record, mapper).getCurrent();
    }

    private <T> SaveOrUpdateResult<T> saveOrReplaceByEsIdWithResult(T record, BaseMapper<T> mapper) {
        String esId = (String) readEsId(record);
        T db = loadByEsId(esId, mapper);
        if (db == null) {
            mapper.insert(record);
            return new SaveOrUpdateResult<>(null, record, true);
        }
        mapper.update(record);
        return new SaveOrUpdateResult<>(db, record, false);
    }

    private <T> T loadByEsId(String esId, BaseMapper<T> mapper) {
        return esId == null ? null : mapper.selectByPrimaryKey(esId);
    }

    private Object readEsId(Object record) {
        try {
            return record.getClass().getMethod("getEsId").invoke(record);
        } catch (Exception e) {
            throw new IllegalStateException("MMBA审计实体缺少 getEsId 方法", e);
        }
    }
}
