import type { TableQueryParams } from '@lib/shared/models/common';

export interface MmbaDevicePageParams extends TableQueryParams {
  um?: string;
  deviceId?: string;
  imei?: string;
  phone?: string;
  staffName?: string;
  deviceStatus?: number;
}

export interface MmbaDevice {
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
  um?: string;
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

export interface MmbaDeviceSaveParams {
  deviceId?: string;
  um?: string;
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

export type MmbaDeviceUpdateParams = MmbaDeviceSaveParams & { id: string };

export interface MmbaDeviceImportResult {
  successCount: number;
  failCount: number;
  errorMessages: { rowNum: number; errMsg: string }[];
}
