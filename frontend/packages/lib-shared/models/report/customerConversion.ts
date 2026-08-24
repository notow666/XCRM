import type { ReportRangeParams } from './contractAnalysis';

export type CustomerConversionEventType = 'VISIT' | 'CONTRACT_SIGNED' | 'PAYMENT_APPROVED';

export interface CustomerConversionSummaryItem {
  dimensionKey: string;
  dimensionLabel: string;
  visitCustomerCount: number;
  signedCustomerCount: number;
  paymentCustomerCount: number;
  signRate: string;
  paymentRate: string;
}

export interface CustomerConversionDetailParams extends Omit<ReportRangeParams, 'dimensionType'> {
  dimensionType: Exclude<ReportRangeParams['dimensionType'], 'CUSTOMER_SOURCE'>;
  dimensionKey: string;
  eventType: CustomerConversionEventType;
  current: number;
  pageSize: number;
}

export interface CustomerConversionDetailItem {
  eventType: CustomerConversionEventType;
  businessId: string;
  customerId: string;
  customerName: string;
  customerMobile?: string;
  employeeId: string;
  employeeName: string;
  departmentId?: string;
  departmentName?: string;
  eventTime: number;
  statDate: string;
}
