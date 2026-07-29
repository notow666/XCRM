import type { TableQueryParams } from '@lib/shared/models/common';

export interface CustomerRecordingPageParams extends TableQueryParams {
  startTime: number;
  endTime: number;
  departmentId?: string;
  employeeId?: string;
  customerName?: string;
  customerTel?: string;
  minDuration?: number;
  maxDuration?: number;
}

export interface CustomerRecordingListItem {
  auditId: string;
  operatorUserId: string;
  employeeName: string;
  departmentId: string;
  departmentName: string;
  customerId: string;
  customerName: string;
  customerTel: string;
  beginTime: string;
  answerTime: string;
  endTime: string;
  duration: number;
}

export interface CustomerRecordingEmployeeOption {
  id: string;
  name: string;
  departmentId: string;
  departmentName: string;
}
