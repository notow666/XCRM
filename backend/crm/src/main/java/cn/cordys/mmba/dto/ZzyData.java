package cn.cordys.mmba.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class ZzyData implements Serializable {
    @Serial
    private static final long serialVersionUID = -1002658283263203427L;
    /**
     * 租户
     */
    private String tenantId;
    /**
     * 公司code
     */
    private String tenancyName;
    /**
     * 审计类型
     */
    private Integer behaviorType;

    /**
     * 应用ID
     */
    private Long appId;

    /**
     * 应用名称
     */
    private String appName;
    /**
     * 数据产生时间
     */
    private String createTime;
    /**
     * 字段 createTime 对应时间的时间戳
     */
    private Long timestamp;
    /**
     * 记录唯一标识
     */
    private String esId;
    /**
     * 备注
     */
    private String note;
    /**
     * 个性签名
     */
    private String sign;
    /**
     * 是否实名认证。0-未知、1-实名、2-未实名
     */
    private String verifiedStatus;
    /**
     * 地区
     */
    private String staffArea;
    /**
     * 业务员微信账号ID
     */
    private String staffIdInApp;
    /**
     * 业务员微信登录账号
     */
    private String staffImAppAccount;
    /**
     * 业务员微信账号图像
     */
    private String staffImAppHeaderPic;
    /**
     * 业务员微信账号昵称
     */
    private String staffImNickName;
    /**
     * 业务员微信账号手机号
     */
    private String staffMobile;
    /**
     * 业务员微信账号性别
     */
    private String staffSex;
    /**
     * 组织机构全路径
     */
    private String orgNames;
    /**
     * 用户所属组织机构名称
     */
    private String orgName;
    /**
     * 用户名称
     */
    private String staffName;
    /**
     * 用户登录名
     */
    private String um;
    /**
     * 当前组织机构id
     */
    private Long deptId;
    /**
     * 当前组织机构全路径
     */
    private String deptIdPath;
    /**
     * 当前组织机构信息
     */
    private String deptInfo;
    /**
     * 数据产生时间戳
     */
    private String insertTime;
    /**
     * 产生数据的设备的唯一标识
     */
    private Long deviceId;
    /**
     * 设备自带的唯一标识属性
     */
    private String imei1;
    /**
     * 设备自带的唯一标识属性
     */
    private String imei2;

    /**
     * 应用ID
     */
    private String contacImAppNote;
    /**
     * 好友备注
     */
    private String contactImAppNote;
    /**
     * 地区
     */
    private String contactArea;
    /**
     * 好友描述
     */
    private String contactDescription;
    /**
     * 好友微信号
     */
    private String contactImAppAccount;
    /**
     * 头像
     */
    private String contactImAppHeaderPic;
    /**
     * 好友微信昵称
     */
    private String contactImAppNickName;
    /**
     *
     */
    private String contactImNickName;
    /**
     * 好友微信id,当isFriend为1时是真实有效的好友微信id
     */
    private String contactImIdInApp;
    /**
     * 好友手机号，多个的话用中文或者英文逗号分隔
     */
    private String contactMobile;
    /**
     * 性别
     */
    private String contactSex;
    /**
     * 好友标签ID
     */
    private String contactWeixinTags;
    /**
     * 好友类型
     */
    private String isFriend;
    /**
     * 设备自带的唯一标识属性
     */
    private String imei;
    /**
     * 操作类型：
     * 1:添加好友，2:删除好友，3:编辑好友，4:对方主动添加好友,5:我方主动添加好友,
     * 6:加入黑名单，7：移出黑名单
     * 备注：
     * 1添加好友【双方均通过并成为好友】
     * 4和5是还未成为好友【对方主动和我方主动是发起添加好友的动作，但是并未通过】
     */
    private String operFlag;
    /**
     * 该字段仅针对“operFlag=1”有效，其他类型时该值不存在
     * 0. operFlag类型不为1，不适用
     * 1.我方主动添加，对方通过；
     * 2.对方主动添加，我方通过；
     * 3.未获取到主被动操作（如朋友验证无法区分）
     * （api套件3.0.9新增）
     */
    private String source;
    /**
     * 添加好友添加方式：该字段仅针对“operFlag=5”有效，其他类型该值不存在
     * 0. operFlag类型不为5，不适用
     * 1. 业务系统API添加；
     * 2. 手机添加；
     * （api套件3.0.9新增）
     */
    private String wxType;
    /**
     * 微信朋友手机号
     */
    private String friendPhone;
    /**
     * 添加好友时搜索的内容，可以是QQ号或者手机号（说明：主动发起时API传递-通过后做关联）
     * （api套件3.0.9新增）
     */
    private String friendSearch;
    /**
     * 公司
     */
    private String company;
    /**
     * 大区
     */
    private String region;
    /**
     * 部门
     */
    private String department;
    /**
     * 聊天内容类型
     */
    private String chatType;
    /**
     * 微信聊天内容
     */
    private String content;
    /**
     * 客户好友的备注
     */
    private String customer;
    /**
     * 客户微信号
     */
    private String customerAccount;
    /**
     * 客户微信ID
     */
    private String customerAccountId;
    /**
     * 客户微信昵称
     */
    private String customerNickname;
    /**
     * 客户微信头像
     */
    private String customerPic;
    /**
     * 聊天信息行为
     */
    private String direction;
    /**
     * 群ID,群聊时才有值
     */
    private String groupId;
    /**
     * 群名称,群聊时才有值
     */
    private String groupName;
    /**
     * 群内发言人微信ID
     */
    private String memberAccount;
    /**
     * 群内发言人昵称
     */
    private String memberNickName;
    /**
     * 群内发言人图片
     */
    private String memberPic;
    /**
     * 业务id
     */
    private String reqId;
    /**
     * 业务员微信账号
     */
    private String staffAccout;
    /**
     * 业务员微信账号
     */
    private String staffAccount;
    /**
     * 业务员微信ID
     */
    private String staffAccoutId;
    /**
     * 业务员微信ID
     */
    private String staffAccountId;
    /**
     * 业务员微信昵称
     */
    private String staffNickname;
    /**
     * 业务员微信头像
     */
    private String staffPic;
    /**
     * 聊天收发状态
     */
    private String status;
    /**
     * 聊天对象类型
     */
    private String targetType;
    /**
     * 消息来源类型
     */
    private String chatSourcesType;
    /**
     * 微信聊天时间
     */
    private String time;
    /**
     * 产生数据的设备的名称
     */
    private String deviceName;
    /**
     * 应用包名
     */
    private String appPkgName;
    /**
     * 登录/登出状态
     */
    private String loginStatus;
    /**
     * 联系人电话
     */
    private String customerTel;
    /**
     * 信息类型
     */
    private String type;
    /**
     * 短信或彩信关联sim的iccid
     */
    private String iccid;
    /**
     * sim卡手机号
     */
    private String iccidPhone;
    /**
     * 彩信主题
     */
    private String subject;
    /**
     * 扩展字段，掉接口发什么，回执上报原样返回
     */
    private String bizExtInfo;
    /**
     * 是否为5G短信
     */
    private String netSms;

    /**
     * 微信绑定的QQ号
     */
    private String qq;

    /**
     * 微信聊天记录音视频、图片url
     */
    private String fileUrl;

    /**
     * 电话拨打时间，不是拨通时间
     */
    private String beginTime;
    /**
     * 字段 beginTime 对应时间的时间戳
     */
    private String beginTimestamp;
    /**
     * 电话挂断时间
     */
    private String endTime;
    /**
     * 字段 endTime 对应时间的时间戳
     */
    private String endTimestamp;
    /**
     * 通话时长（秒）
     */
    private String duration;
    /**
     * 通话录音文件
     */
    private String record;

    /**
     * 运营商：0，未知；1，中国移动；2，中国联通；3，中国电信；4，Telekomsel
     */
    private String mobileVendor;
    /**
     * 接通状态：0，未接通；1，接通；2，受限挂断
     */
    private int isConnected;
    /**
     * 拨打电话的发起类型：1，业务系统发起； 2，手机发起
     */
    private String callType;
    /**
     * 设备录音声道信息：0，不区分左右声道；1，左声道为对方右声道为自己；
     * 2，左声道为自己右声道为对方；3，不清楚声道信息
     */
    private String soundChannel;

    /**
     * 该通通话为重试上报，由客户端发起。
     */
    private String isRetry;
}
