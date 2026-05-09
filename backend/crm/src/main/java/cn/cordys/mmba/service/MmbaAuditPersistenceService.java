package cn.cordys.mmba.service;

import cn.cordys.common.uid.IDGenerator;
import cn.cordys.mmba.domain.MmbaCallRecordAudit;
import cn.cordys.mmba.domain.MmbaDeviceInfoAudit;
import cn.cordys.mmba.domain.MmbaDeviceStatusAudit;
import cn.cordys.mmba.domain.MmbaSmsRecordAudit;
import cn.cordys.mmba.domain.MmbaWxAccountAudit;
import cn.cordys.mmba.domain.MmbaWxChatAudit;
import cn.cordys.mmba.domain.MmbaWxFriendChangeAudit;
import cn.cordys.mmba.domain.MmbaWxFriendListAudit;
import cn.cordys.mmba.domain.MmbaWxLoginAudit;
import cn.cordys.mybatis.BaseMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MMBA 审计分表持久化服务。
 * 新结构下：
 * 1. call/sms/wx* 这几张表直接按 esId 做主键和幂等；
 * 2. deviceInfo/deviceStatus/wxLogin 保留本地 id，但不再依赖 BaseModel 或 organization_id。
 */
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
    @Resource
    private BaseMapper<MmbaDeviceStatusAudit> mmbaDeviceStatusAuditMapper;

    public MmbaCallRecordAudit saveOrUpdateCallAudit(MmbaCallRecordAudit record, String userId) {
        return saveOrReplaceByEsId(record, mmbaCallRecordAuditMapper);
    }

    public MmbaSmsRecordAudit saveOrUpdateSmsAudit(MmbaSmsRecordAudit record, String userId) {
        return saveOrReplaceByEsId(record, mmbaSmsRecordAuditMapper);
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

    public MmbaDeviceStatusAudit saveOrUpdateDeviceStatusAudit(MmbaDeviceStatusAudit record, String userId) {
        MmbaDeviceStatusAudit query = new MmbaDeviceStatusAudit();
        query.setDeviceId(record.getDeviceId());
        query.setDeviceStatus(record.getDeviceStatus());
        query.setChangeTime(record.getChangeTime());
        MmbaDeviceStatusAudit db = mmbaDeviceStatusAuditMapper.selectOne(query);
        if (db == null) {
            record.setId(IDGenerator.nextStr());
            mmbaDeviceStatusAuditMapper.insert(record);
            return record;
        }
        record.setId(db.getId());
        mmbaDeviceStatusAuditMapper.update(record);
        return record;
    }

    private <T> T saveOrReplaceByEsId(T record, BaseMapper<T> mapper) {
        Object esId = readEsId(record);
        T db = mapper.selectByPrimaryKey((String) esId);
        if (db == null) {
            mapper.insert(record);
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
