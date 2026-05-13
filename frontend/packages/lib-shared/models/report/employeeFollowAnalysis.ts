import type { TableQueryParams } from '@lib/shared/models/common';

export interface EmployeeFollowAnalysisSummaryParams {
  timePreset: string;
  startTime?: number;
  endTime?: number;
  dimensionType: string;
  showEmptyItems?: boolean;
}

export interface EmployeeFollowAnalysisSummaryItem {
  dimensionKey: string;
  dimensionLabel: string;
  inboundCustomerCount: number;
  contactedCustomerCount: number;
  newWechatFriendCount: number;
  dialCount: number;
  connectedCount: number;
  callOver1MinCount: number;
  callOver3MinCount: number;
  callDurationSec: number;
  avgCallDurationSec: number;
}

export interface EmployeeFollowAnalysisDrilldownParams extends TableQueryParams {
  timePreset: string;
  startTime?: number;
  endTime?: number;
  dimensionType: string;
  dimensionKey: string;
  metricType: string;
}

export interface EmployeeFollowAnalysisDrilldownItem {
  auditId?: string;
  customerId?: string;
  customerName?: string;
  mobile?: string;
  ownerName?: string;
  departmentName?: string;
  customerSource?: string;
  eventTime?: number;
  friendPhone?: string;
  contactImAppNickName?: string;
  contactImAppAccount?: string;
  contactImAppNote?: string;
  staffName?: string;
  beginTime?: string;
  endTime?: string;
  duration?: number;
  isConnected?: number;
  direction?: number;
}
