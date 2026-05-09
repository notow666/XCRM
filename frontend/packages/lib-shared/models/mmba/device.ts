import type { TableQueryParams } from '@lib/shared/models/common';

export interface MmbaDevicePageParams extends TableQueryParams {
  deviceId?: string;
  imei?: string;
  phone?: string;
  staffName?: string;
  deviceStatus?: number;
}

export interface MmbaDevice {
  id: string;
  enable?: boolean;
  deviceId?: string;
  deviceName?: string;
  deviceType?: string;
  deviceStatus?: number;
  imei?: string;
  imei2?: string;
  iccid?: string;
  iccid2?: string;
  phone?: string;
  phone2?: string;
  telecomOperators?: string;
  telecomOperators2?: string;
  staffName?: string;
  orgName?: string;
  orgNames?: string;
  /** 毫秒时间戳，由后端根据 lastOnlineTime 写入 */
  lastOnline?: number;
  lastOnlineTime?: string;
  loginStatus?: number;
  createTime?: number;
  updateTime?: number;
}

/** 与后端一致：主键 id 即为 UM */
export interface MmbaDeviceSaveParams {
  id: string;
  deviceId?: string;
  deviceName?: string;
  deviceType?: string;
  deviceStatus?: number;
  imei?: string;
  imei2?: string;
  iccid?: string;
  iccid2?: string;
  phone?: string;
  phone2?: string;
  telecomOperators?: string;
  telecomOperators2?: string;
  staffName?: string;
  orgName?: string;
  orgNames?: string;
  lastOnlineTime?: string;
  loginStatus?: number;
}

export type MmbaDeviceUpdateParams = MmbaDeviceSaveParams;

export interface MmbaDeviceImportResult {
  successCount: number;
  failCount: number;
  errorMessages: { rowNum: number; errMsg: string }[];
}
