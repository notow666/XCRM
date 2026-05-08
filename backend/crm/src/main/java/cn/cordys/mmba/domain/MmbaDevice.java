package cn.cordys.mmba.domain;

import cn.cordys.common.domain.BaseModel;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Table(name = "mmba_device")
public class MmbaDevice extends BaseModel {
    /**
     * 设备ID
     */
    private String deviceId;
    /**
     * 设备名称
     */
    private String deviceName;
    /**
     * 设备型号
     */
    private String deviceType;
    /**
     * 设备状态
     */
    private Integer deviceStatus;
    /**
     * IMEI
     */
    private String imei;
    /**
     * IMEI
     */
    private String imei2;
    /**
     * ICCID
     */
    private String iccid;
    /**
     * ICCID
     */
    private String iccid2;
    /**
     * 手机号
     */
    private String phone;
    /**
     * 手机号
     */
    private String phone2;
    /**
     * 运营商
     */
    private String telecomOperators;
    /**
     * 运营商
     */
    private String telecomOperators2;
    /**
     * 用户名
     */
    private String um;
    /**
     * 姓名
     */
    private String staffName;
    /**
     * 部门
     */
    private String orgName;
    /**
     *
     */
    private String orgNames;
    /**
     * 最后上线时间戳
     */
    private Long lastOnline;
    /**
     * 最后上线时间
     */
    private String lastOnlineTime;
    /**
     * 在线状态
     */
    private Integer loginStatus;
    /**
     *
     */
    private Integer lastBehaviorType;
    /**
     *
     */
    private Long lastAuditTime;
    /**
     *
     */
    private String rawData;
}
