package cn.cordys.mmba.service;

import cn.cordys.common.uid.IDGenerator;
import cn.cordys.mmba.domain.MmbaCallRecordAudit;
import cn.cordys.mmba.domain.MmbaDeviceInfoAudit;
import cn.cordys.mmba.domain.MmbaSmsRecordAudit;
import cn.cordys.mmba.domain.MmbaWxAccountAudit;
import cn.cordys.mmba.domain.MmbaWxChatAudit;
import cn.cordys.mmba.domain.MmbaWxFriendChangeAudit;
import cn.cordys.mmba.domain.MmbaWxFriendListAudit;
import cn.cordys.mmba.domain.MmbaWxLoginAudit;
import cn.cordys.mybatis.BaseMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MMBA 审计分表持久化服务。
 * 新结构下：
 * 1. call/sms/wx* 这几张表直接按 esId 做主键和幂等；
 * 2. deviceInfo/deviceStatus/wxLogin 保留本地 id，但不再依赖 BaseModel 或 organization_id。
 */
@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class MmbaAuditPersistenceService {

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
    private BaseMapper<MmbaDeviceInfoAudit> mmbaDeviceInfoAuditMapper;

    public MmbaCallRecordAudit saveOrUpdateCallAudit(MmbaCallRecordAudit record, String userId) {
        return saveOrReplaceByEsId(record, mmbaCallRecordAuditMapper);
    }

    public MmbaCallRecordAudit findCallAuditByEsId(String esId) {
        return esId == null ? null : mmbaCallRecordAuditMapper.selectByPrimaryKey(esId);
    }

    public MmbaSmsRecordAudit saveOrUpdateSmsAudit(MmbaSmsRecordAudit record, String userId) {
        return saveOrReplaceByEsId(record, mmbaSmsRecordAuditMapper);
    }

    public MmbaSmsRecordAudit findSmsAuditByEsId(String esId) {
        return esId == null ? null : mmbaSmsRecordAuditMapper.selectByPrimaryKey(esId);
    }

    public MmbaWxChatAudit findWxChatAuditByEsId(String esId) {
        return esId == null ? null : mmbaWxChatAuditMapper.selectByPrimaryKey(esId);
    }

    public MmbaWxAccountAudit saveOrUpdateWxAccountAudit(MmbaWxAccountAudit record, String userId) {
        return saveOrReplaceByEsId(record, mmbaWxAccountAuditMapper);
    }

    public MmbaWxChatAudit saveOrUpdateWxChatAudit(MmbaWxChatAudit record, String userId) {
        return saveOrReplaceByEsId(record, mmbaWxChatAuditMapper);
    }

    public MmbaWxFriendChangeAudit saveOrUpdateWxFriendChangeAudit(MmbaWxFriendChangeAudit record, String userId) {
        return saveOrReplaceByEsId(record, mmbaWxFriendChangeAuditMapper);
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
        MmbaDeviceInfoAudit query = new MmbaDeviceInfoAudit();
        query.setDeviceId(record.getDeviceId());
        query.setTimestamp(record.getTimestamp());
        MmbaDeviceInfoAudit db = mmbaDeviceInfoAuditMapper.selectOne(query);
        if (db == null) {
            record.setId(IDGenerator.nextStr());
            mmbaDeviceInfoAuditMapper.insert(record);
            return record;
        }
        record.setId(db.getId());
        mmbaDeviceInfoAuditMapper.update(record);
        return record;
    }

    private <T> T saveOrReplaceByEsId(T record, BaseMapper<T> mapper) {
        String esId = (String) readEsId(record);
        T db = mapper.selectByPrimaryKey(esId);
        if (db == null) {
            try {
                mapper.insert(record);
            } catch (DuplicateKeyException e) {
                // 并发消费同一 esId 时，其他线程可能已经完成插入，这里直接转更新即可。
                log.info("MMBA审计并发幂等转更新 esId={} entity={}", esId, record.getClass().getSimpleName());
                mapper.update(record);
            }
            return record;
        }
        mapper.update(record);
        return record;
    }

    private Object readEsId(Object record) {
        try {
            return record.getClass().getMethod("getEsId").invoke(record);
        } catch (Exception e) {
            throw new IllegalStateException("MMBA审计实体缺少 getEsId 方法", e);
        }
    }
}
