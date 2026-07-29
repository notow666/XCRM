package cn.cordys.mmba.service;

import cn.cordys.common.exception.GenericException;
import cn.cordys.crm.system.domain.User;
import cn.cordys.crm.system.mapper.ExtUserExtendMapper;
import cn.cordys.mmba.domain.MmbaDevice;
import cn.cordys.mmba.dto.request.MmbaPhonePreferenceUpdateRequest;
import cn.cordys.mmba.dto.response.MmbaPhonePreferenceResponse;
import cn.cordys.mybatis.BaseMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MmbaPhonePreferenceServiceTest {

    private static final String USER_ID = "user-1";
    private static final String UM = "um-1";

    @Mock
    private BaseMapper<User> userBaseMapper;
    @Mock
    private ExtUserExtendMapper extUserExtendMapper;
    @Mock
    private MmbaDeviceService mmbaDeviceService;
    @InjectMocks
    private MmbaPhonePreferenceService mmbaPhonePreferenceService;

    @Test
    void shouldReturnAvailableCardSlotsAndFullPhones() {
        User user = buildUser();
        MmbaDevice device = new MmbaDevice();
        device.setPhone("13812345678");
        device.setIccid2("iccid-2");

        when(userBaseMapper.selectByPrimaryKey(USER_ID)).thenReturn(user);
        when(mmbaDeviceService.getDevice(UM)).thenReturn(device);
        when(extUserExtendMapper.selectDefaultCallCardSlotNum(USER_ID)).thenReturn(1);

        MmbaPhonePreferenceResponse response = mmbaPhonePreferenceService.getPreference(USER_ID);

        Assertions.assertEquals(1, response.getDefaultCardSlotNum());
        Assertions.assertTrue(response.getUmConfigured());
        Assertions.assertTrue(response.getDeviceConfigured());
        Assertions.assertEquals("13812345678", response.getCardSlots().getFirst().getPhone());
        Assertions.assertTrue(response.getCardSlots().getFirst().getAvailable());
        Assertions.assertTrue(response.getCardSlots().get(1).getAvailable());
    }

    @Test
    void shouldSaveAvailableDefaultCardSlot() {
        User user = buildUser();
        MmbaDevice device = new MmbaDevice();
        device.setPhone2("13912345678");
        MmbaPhonePreferenceUpdateRequest request = new MmbaPhonePreferenceUpdateRequest();
        request.setDefaultCardSlotNum(2);

        when(userBaseMapper.selectByPrimaryKey(USER_ID)).thenReturn(user);
        when(mmbaDeviceService.getDevice(UM)).thenReturn(device);
        when(extUserExtendMapper.selectDefaultCallCardSlotNum(USER_ID)).thenReturn(2);

        MmbaPhonePreferenceResponse response = mmbaPhonePreferenceService.updatePreference(request, USER_ID);

        verify(extUserExtendMapper).upsertDefaultCallCardSlotNum(USER_ID, 2);
        Assertions.assertEquals(2, response.getDefaultCardSlotNum());
    }

    @Test
    void shouldRejectUnavailableDefaultCardSlot() {
        User user = buildUser();
        MmbaPhonePreferenceUpdateRequest request = new MmbaPhonePreferenceUpdateRequest();
        request.setDefaultCardSlotNum(2);

        when(userBaseMapper.selectByPrimaryKey(USER_ID)).thenReturn(user);
        when(mmbaDeviceService.getDevice(UM)).thenReturn(new MmbaDevice());

        GenericException exception = Assertions.assertThrows(
                GenericException.class,
                () -> mmbaPhonePreferenceService.updatePreference(request, USER_ID)
        );

        Assertions.assertEquals("当前登录人下未配置卡槽2", exception.getMessage());
    }

    @Test
    void shouldClearDefaultCardSlot() {
        MmbaPhonePreferenceUpdateRequest request = new MmbaPhonePreferenceUpdateRequest();
        request.setDefaultCardSlotNum(null);

        mmbaPhonePreferenceService.updatePreference(request, USER_ID);

        verify(extUserExtendMapper).upsertDefaultCallCardSlotNum(USER_ID, null);
    }

    private User buildUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setUm(UM);
        return user;
    }
}
