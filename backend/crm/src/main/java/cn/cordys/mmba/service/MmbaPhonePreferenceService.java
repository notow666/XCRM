package cn.cordys.mmba.service;

import cn.cordys.common.exception.GenericException;
import cn.cordys.crm.system.domain.User;
import cn.cordys.crm.system.mapper.ExtUserExtendMapper;
import cn.cordys.mmba.domain.MmbaDevice;
import cn.cordys.mmba.dto.request.MmbaPhonePreferenceUpdateRequest;
import cn.cordys.mmba.dto.response.MmbaPhoneCardSlotResponse;
import cn.cordys.mmba.dto.response.MmbaPhonePreferenceResponse;
import cn.cordys.mybatis.BaseMapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MmbaPhonePreferenceService {

    private static final int CARD_SLOT_ONE = 1;
    private static final int CARD_SLOT_TWO = 2;

    @Resource
    private BaseMapper<User> userBaseMapper;
    @Resource
    private ExtUserExtendMapper extUserExtendMapper;
    @Resource
    private MmbaDeviceService mmbaDeviceService;

    public MmbaPhonePreferenceResponse getPreference(String userId) {
        User user = userBaseMapper.selectByPrimaryKey(userId);
        String um = user == null ? null : StringUtils.trimToNull(user.getUm());
        MmbaDevice device = StringUtils.isBlank(um) ? null : mmbaDeviceService.getDevice(um);

        MmbaPhonePreferenceResponse response = new MmbaPhonePreferenceResponse();
        response.setDefaultCardSlotNum(getDefaultCardSlotNum(userId));
        response.setUmConfigured(StringUtils.isNotBlank(um));
        response.setDeviceConfigured(device != null);
        response.setCardSlots(List.of(buildCardSlot(device, CARD_SLOT_ONE), buildCardSlot(device, CARD_SLOT_TWO)));
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public MmbaPhonePreferenceResponse updatePreference(MmbaPhonePreferenceUpdateRequest request, String userId) {
        Integer cardSlotNum = request.getDefaultCardSlotNum();
        if (cardSlotNum != null) {
            validateCardSlotNum(cardSlotNum);
            User user = userBaseMapper.selectByPrimaryKey(userId);
            String um = user == null ? null : StringUtils.trimToNull(user.getUm());
            if (StringUtils.isBlank(um)) {
                throw new GenericException("当前登录人未配置UM，无法设置默认拨号卡");
            }
            MmbaDevice device = mmbaDeviceService.getDevice(um);
            if (device == null) {
                throw new GenericException("当前登录人未配置MMBA设备");
            }
            if (!isCardSlotAvailable(device, cardSlotNum)) {
                throw new GenericException("当前登录人下未配置卡槽" + cardSlotNum);
            }
        }
        extUserExtendMapper.upsertDefaultCallCardSlotNum(userId, cardSlotNum);
        return getPreference(userId);
    }

    public Integer getDefaultCardSlotNum(String userId) {
        return extUserExtendMapper.selectDefaultCallCardSlotNum(userId);
    }

    public boolean isCardSlotAvailable(MmbaDevice device, int cardSlotNum) {
        if (device == null) {
            return false;
        }
        if (cardSlotNum == CARD_SLOT_ONE) {
            return StringUtils.isNotBlank(device.getPhone()) || StringUtils.isNotBlank(device.getIccid());
        }
        if (cardSlotNum == CARD_SLOT_TWO) {
            return StringUtils.isNotBlank(device.getPhone2()) || StringUtils.isNotBlank(device.getIccid2());
        }
        return false;
    }

    private MmbaPhoneCardSlotResponse buildCardSlot(MmbaDevice device, int cardSlotNum) {
        MmbaPhoneCardSlotResponse response = new MmbaPhoneCardSlotResponse();
        response.setCardSlotNum(cardSlotNum);
        response.setAvailable(isCardSlotAvailable(device, cardSlotNum));
        String phone = null;
        if (device != null) {
            phone = cardSlotNum == CARD_SLOT_ONE ? device.getPhone() : device.getPhone2();
        }
        response.setPhone(StringUtils.trimToNull(phone));
        return response;
    }

    private void validateCardSlotNum(int cardSlotNum) {
        if (cardSlotNum != CARD_SLOT_ONE && cardSlotNum != CARD_SLOT_TWO) {
            throw new GenericException("默认拨号卡参数无效");
        }
    }
}
