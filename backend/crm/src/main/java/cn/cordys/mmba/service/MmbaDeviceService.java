package cn.cordys.mmba.service;

import cn.cordys.common.exception.GenericException;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.TimeUtils;
import cn.cordys.common.util.Translator;
import cn.cordys.mmba.domain.MmbaDevice;
import cn.cordys.mmba.domain.MmbaDeviceMapping;
import cn.cordys.mmba.dto.request.MmbaDeviceSaveRequest;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * MMBA 设备主表和设备映射表服务。
 * 设备类回调除了落流水，还会维护最新快照和设备-员工-微信关系。
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class MmbaDeviceService {

    @Resource
    private BaseMapper<MmbaDevice> mmbaDeviceMapper;
    @Resource
    private BaseMapper<MmbaDeviceMapping> mmbaDeviceMappingMapper;

    /**
     * 保存或更新设备主表快照。
     */
    public MmbaDevice saveOrUpdateDevice(MmbaDevice device, String userId) {
        MmbaDevice db = findDevice(device.getId());
        if (db == null) {
            long now = System.currentTimeMillis();
            if (device.getEnable() == null) {
                device.setEnable(Boolean.TRUE);
            }
            device.setCreateTime(now);
            device.setCreateUser(userId);
            device.setUpdateTime(now);
            device.setUpdateUser(userId);
            mmbaDeviceMapper.insert(device);
            return device;
        }
        if (isOlderSnapshot(device, db)) {
            return db;
        }
        mergeDevice(db, device);
        touch(db, userId);
        mmbaDeviceMapper.update(db);
        return db;
    }

    public MmbaDevice getDevice(String id) {
        return mmbaDeviceMapper.selectByPrimaryKey(id);
    }

    public MmbaDevice addDevice(MmbaDeviceSaveRequest request, String userId) {
        MmbaDevice device = new MmbaDevice();
        device.setId(StringUtils.trimToNull(request.getId()));
        device.setDeviceId(StringUtils.trimToNull(request.getDeviceId()));
        device.setDeviceName(request.getDeviceName());
        device.setDeviceType(request.getDeviceType());
        device.setDeviceStatus(request.getDeviceStatus());
        device.setImei(request.getImei());
        device.setImei2(request.getImei2());
        device.setIccid(request.getIccid());
        device.setIccid2(request.getIccid2());
        device.setPhone(request.getPhone());
        device.setPhone2(request.getPhone2());
        device.setTelecomOperators(request.getTelecomOperators());
        device.setTelecomOperators2(request.getTelecomOperators2());
        device.setStaffName(request.getStaffName());
        device.setOrgName(request.getOrgName());
        device.setOrgNames(request.getOrgNames());
        String lastOnlineTime = StringUtils.trimToNull(request.getLastOnlineTime());
        device.setLastOnlineTime(lastOnlineTime);
        device.setLastOnline(TimeUtils.getEpochMillisOrNull(lastOnlineTime));
        device.setLoginStatus(request.getLoginStatus());
        return saveOrUpdateDevice(device, userId);
    }

    public MmbaDevice updateDevice(MmbaDeviceSaveRequest request, String userId) {
        MmbaDevice patch = new MmbaDevice();
        patch.setId(StringUtils.trimToNull(request.getId()));
        if (request.getDeviceId() != null) {
            patch.setDeviceId(StringUtils.trimToNull(request.getDeviceId()));
        }
        if (request.getDeviceName() != null) {
            patch.setDeviceName(request.getDeviceName());
        }
        if (request.getDeviceType() != null) {
            patch.setDeviceType(request.getDeviceType());
        }
        if (request.getDeviceStatus() != null) {
            patch.setDeviceStatus(request.getDeviceStatus());
        }
        if (request.getImei() != null) {
            patch.setImei(request.getImei());
        }
        if (request.getImei2() != null) {
            patch.setImei2(request.getImei2());
        }
        if (request.getIccid() != null) {
            patch.setIccid(request.getIccid());
        }
        if (request.getIccid2() != null) {
            patch.setIccid2(request.getIccid2());
        }
        if (request.getPhone() != null) {
            patch.setPhone(request.getPhone());
        }
        if (request.getPhone2() != null) {
            patch.setPhone2(request.getPhone2());
        }
        if (request.getTelecomOperators() != null) {
            patch.setTelecomOperators(request.getTelecomOperators());
        }
        if (request.getTelecomOperators2() != null) {
            patch.setTelecomOperators2(request.getTelecomOperators2());
        }
        if (request.getStaffName() != null) {
            patch.setStaffName(request.getStaffName());
        }
        if (request.getOrgName() != null) {
            patch.setOrgName(request.getOrgName());
        }
        if (request.getOrgNames() != null) {
            patch.setOrgNames(request.getOrgNames());
        }
        if (request.getLastOnlineTime() != null) {
            if (StringUtils.isBlank(request.getLastOnlineTime())) {
                patch.setLastOnlineTime(StringUtils.EMPTY);
                patch.setLastOnline(null);
            } else {
                String lastOnlineTime = StringUtils.trimToNull(request.getLastOnlineTime());
                patch.setLastOnlineTime(lastOnlineTime);
                patch.setLastOnline(TimeUtils.getEpochMillisOrNull(lastOnlineTime));
            }
        }
        if (request.getLoginStatus() != null) {
            patch.setLoginStatus(request.getLoginStatus());
        }
        return updateDeviceById(request.getId(), patch, userId);
    }

    public MmbaDevice updateDeviceById(String id, MmbaDevice patch, String userId) {
        MmbaDevice db = mmbaDeviceMapper.selectByPrimaryKey(id);
        if (db == null) {
            throw new GenericException(Translator.get("mmba_device_not_found"));
        }
        mergeDevice(db, patch);
        touch(db, userId);
        mmbaDeviceMapper.update(db);
        return db;
    }

    /**
     * 保存或更新设备映射关系。
     */
    public MmbaDeviceMapping saveOrUpdateMapping(MmbaDeviceMapping mapping, String userId) {
        MmbaDeviceMapping db = findMapping(mapping);
        if (db == null) {
            init(mapping, userId);
            mmbaDeviceMappingMapper.insert(mapping);
            return mapping;
        }
        mergeMapping(db, mapping);
        touch(db, userId);
        mmbaDeviceMappingMapper.update(db);
        return db;
    }

    /**
     * 仅更新已存在映射的状态，不新增记录。
     */
    public boolean updateMappingStatus(String um, String wxid, String mappingStatus, String userId) {
        if (StringUtils.isBlank(um) || StringUtils.isBlank(wxid) || StringUtils.isBlank(mappingStatus)) {
            return false;
        }
        MmbaDeviceMapping query = new MmbaDeviceMapping();
        query.setUm(um);
        query.setWxid(wxid);
        MmbaDeviceMapping db = mmbaDeviceMappingMapper.selectOne(query);
        if (db == null) {
            return false;
        }
        db.setMappingStatus(mappingStatus);
        touch(db, userId);
        mmbaDeviceMappingMapper.update(db);
        return true;
    }

    public List<MmbaDeviceMapping> listMappingsByUm(String um) {
        if (StringUtils.isBlank(um)) {
            return List.of();
        }
        LambdaQueryWrapper<MmbaDeviceMapping> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MmbaDeviceMapping::getUm, um)
                .orderByDesc(MmbaDeviceMapping::getLastSyncTime)
                .orderByDesc(MmbaDeviceMapping::getUpdateTime);
        return mmbaDeviceMappingMapper.selectListByLambda(wrapper);
    }

    private MmbaDevice findDevice(String um) {
        if (StringUtils.isBlank(um)) {
            return null;
        }
        return mmbaDeviceMapper.selectByPrimaryKey(um);
    }

    /**
     * 当前映射表默认按 um + imei + wxid 判定唯一。
     */
    private MmbaDeviceMapping findMapping(MmbaDeviceMapping mapping) {
        if (mapping == null) {
            return null;
        }
        MmbaDeviceMapping query = new MmbaDeviceMapping();
        query.setUm(mapping.getUm());
        query.setWxid(mapping.getWxid());
        if (StringUtils.isBlank(query.getUm()) && StringUtils.isBlank(query.getWxid())) {
            return null;
        }
        return mmbaDeviceMappingMapper.selectOne(query);
    }

    private void mergeMapping(MmbaDeviceMapping target, MmbaDeviceMapping source) {
        target.setUm(firstNotBlank(source.getUm(), target.getUm()));
        target.setStaffName(firstNotBlank(source.getStaffName(), target.getStaffName()));
        target.setDeviceId(firstNotBlank(source.getDeviceId(), target.getDeviceId()));
        target.setImei(firstNotBlank(source.getImei(), target.getImei()));
        target.setImei2(firstNotBlank(source.getImei2(), target.getImei2()));
        target.setIccid(firstNotBlank(source.getIccid(), target.getIccid()));
        target.setWxid(firstNotBlank(source.getWxid(), target.getWxid()));
        target.setWxAccount(firstNotBlank(source.getWxAccount(), target.getWxAccount()));
        target.setWxPhone(firstNotBlank(source.getWxPhone(), target.getWxPhone()));
        target.setWxNickName(firstNotBlank(source.getWxNickName(), target.getWxNickName()));
        target.setWxHeaderPic(firstNotBlank(source.getWxHeaderPic(), target.getWxHeaderPic()));
        target.setQq(firstNotBlank(source.getQq(), target.getQq()));
        target.setMappingStatus(firstNotBlank(source.getMappingStatus(), target.getMappingStatus()));
        target.setLastSyncTime(source.getLastSyncTime() == null ? target.getLastSyncTime() : source.getLastSyncTime());
        target.setRawData(firstNotBlank(source.getRawData(), target.getRawData()));
    }

    private void mergeDevice(MmbaDevice target, MmbaDevice source) {
        target.setDeviceId(firstNotBlank(source.getDeviceId(), target.getDeviceId()));
        target.setDeviceName(firstNotBlank(source.getDeviceName(), target.getDeviceName()));
        target.setDeviceType(firstNotBlank(source.getDeviceType(), target.getDeviceType()));
        target.setDeviceStatus(source.getDeviceStatus() == null ? target.getDeviceStatus() : source.getDeviceStatus());
        target.setImei(firstNotBlank(source.getImei(), target.getImei()));
        target.setImei2(firstNotBlank(source.getImei2(), target.getImei2()));
        target.setIccid(firstNotBlank(source.getIccid(), target.getIccid()));
        target.setIccid2(firstNotBlank(source.getIccid2(), target.getIccid2()));
        target.setPhone(firstNotBlank(source.getPhone(), target.getPhone()));
        target.setPhone2(firstNotBlank(source.getPhone2(), target.getPhone2()));
        target.setTelecomOperators(firstNotBlank(source.getTelecomOperators(), target.getTelecomOperators()));
        target.setTelecomOperators2(firstNotBlank(source.getTelecomOperators2(), target.getTelecomOperators2()));
        target.setStaffName(firstNotBlank(source.getStaffName(), target.getStaffName()));
        target.setOrgName(firstNotBlank(source.getOrgName(), target.getOrgName()));
        target.setOrgNames(firstNotBlank(source.getOrgNames(), target.getOrgNames()));
        if (source.getLastOnlineTime() != null) {
            if (StringUtils.EMPTY.equals(source.getLastOnlineTime())) {
                target.setLastOnlineTime(null);
                target.setLastOnline(null);
            } else {
                target.setLastOnlineTime(source.getLastOnlineTime());
                Long ms = source.getLastOnline() != null
                        ? source.getLastOnline()
                        : TimeUtils.getEpochMillisOrNull(source.getLastOnlineTime());
                target.setLastOnline(ms);
            }
        } else if (source.getLastOnline() != null) {
            target.setLastOnline(source.getLastOnline());
        }
        target.setLoginStatus(source.getLoginStatus() == null ? target.getLoginStatus() : source.getLoginStatus());
        target.setLastBehaviorType(source.getLastBehaviorType() == null ? target.getLastBehaviorType() : source.getLastBehaviorType());
        target.setLastAuditTime(source.getLastAuditTime() == null ? target.getLastAuditTime() : source.getLastAuditTime());
        target.setRawData(firstNotBlank(source.getRawData(), target.getRawData()));
    }

    /**
     * 初始化新增记录的通用审计字段。
     */
    private void init(cn.cordys.common.domain.BaseModel record, String userId) {
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
    private void touch(cn.cordys.common.domain.BaseModel record, String userId) {
        record.setUpdateTime(System.currentTimeMillis());
        record.setUpdateUser(userId);
    }

    private String firstNotBlank(String preferred, String fallback) {
        return StringUtils.isNotBlank(preferred) ? preferred : fallback;
    }

    private boolean isOlderSnapshot(MmbaDevice source, MmbaDevice target) {
        if (source == null || source.getLastAuditTime() == null || target == null || target.getLastAuditTime() == null) {
            return false;
        }
        return source.getLastAuditTime() < target.getLastAuditTime();
    }
}
