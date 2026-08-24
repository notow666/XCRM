export interface ReportRangeParams {
  startTime?: number;
  endTime?: number;
  dimensionType: 'EMPLOYEE_NAME' | 'EMPLOYEE_DEPT' | 'CUSTOMER_SOURCE' | 'STAT_DAY' | 'STAT_MONTH';
  departmentId?: string;
}

export interface ContractAnalysisSummaryItem {
  dimensionKey: string;
  dimensionLabel: string;
  contractCount: number;
  contractAmount: number;
  loanAmount: number;
  repaymentAmount: number;
  revenueAmount: number;
}

export interface ContractAnalysisDetailParams extends ReportRangeParams {
  dimensionKey: string;
  metricType: 'CONTRACT' | 'LOAN' | 'REPAYMENT' | 'REVENUE';
  current: number;
  pageSize: number;
}

export interface ContractAnalysisDetailItem {
  metricType: string;
  resourceId: string;
  paymentRecordId?: string;
  contractId: string;
  contractName: string;
  customerId: string;
  customerName: string;
  customerMobile?: string;
  customerSource?: string;
  signerId: string;
  signerName: string;
  departmentId?: string;
  departmentName?: string;
  businessTime: number;
  amount: number;
}
