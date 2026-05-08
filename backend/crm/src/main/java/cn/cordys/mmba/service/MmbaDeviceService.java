package cn.cordys.mmba.service;

import cn.cordys.common.uid.IDGenerator;
import cn.cordys.mmba.domain.MmbaDevice;
import cn.cordys.mmba.domain.MmbaDeviceMapping;
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
        MmbaDevice db = findDevice(device.getUm(), device.getDeviceId());
        if (db == null) {
            init(device, userId);
            mmbaDeviceMapper.insert(device);
            return device;
        }
        mergeDevice(db, device);
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

    /**
     * 优先按 deviceId 查，查不到再按 imei 查。
     */
    public List<MmbaDevice> listByUm(String um) {
        if (StringUtils.isBlank(um)) {
            return List.of();
        }
        LambdaQueryWrapper<MmbaDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MmbaDevice::getUm, um)
                .orderByDesc(MmbaDevice::getUpdateTime);
        return mmbaDeviceMapper.selectListByLambda(wrapper);
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

    private MmbaDevice findDevice(String um, String deviceId) {
        if (StringUtils.isBlank(um) || StringUtils.isBlank(deviceId)) {
            return null;
        }
        MmbaDevice query = new MmbaDevice();
        query.setUm(um);
        query.setDeviceId(deviceId);
        return mmbaDeviceMapper.selectOne(query);
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
        target.setUm(firstNotBlank(source.getUm(), target.getUm()));
        target.setStaffName(firstNotBlank(source.getStaffName(), target.getStaffName()));
        target.setOrgName(firstNotBlank(source.getOrgName(), target.getOrgName()));
        target.setOrgNames(firstNotBlank(source.getOrgNames(), target.getOrgNames()));
        target.setLastOnline(source.getLastOnline() == null ? target.getLastOnline() : source.getLastOnline());
        target.setLastOnlineTime(firstNotBlank(source.getLastOnlineTime(), target.getLastOnlineTime()));
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
}
