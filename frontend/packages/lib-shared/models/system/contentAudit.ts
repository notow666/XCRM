import type { TableQueryParams } from '../common';

export interface WechatAccountStatTableParams extends TableQueryParams {}

export interface WechatAccountStatItem {
  id: string;
  um: string;
  employeeName: string;
  departmentName: string;
  deviceName: string;
  deviceStatus?: number | null;
  deviceDisplay: string;
  wxHeaderPic: string;
  wxNickName: string;
  wxAccount: string;
  friendCount: string;
  chatRecordCount: string;
  lastSyncTime?: number | null;
  updateTime?: number | null;
}
