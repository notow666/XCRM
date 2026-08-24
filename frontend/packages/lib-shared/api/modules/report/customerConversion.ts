import type { CordysAxios } from '@lib/shared/api/http/Axios';
import {
  CustomerConversionDetailExportUrl,
  CustomerConversionDetailUrl,
  CustomerConversionExportUrl,
  CustomerConversionSummaryUrl,
} from '@lib/shared/api/requrls/report/customerConversion';
import type { CommonList } from '@lib/shared/models/common';
import type { ReportRangeParams } from '@lib/shared/models/report/contractAnalysis';
import type {
  CustomerConversionDetailItem,
  CustomerConversionDetailParams,
  CustomerConversionSummaryItem,
} from '@lib/shared/models/report/customerConversion';

export default function useCustomerConversionApi(CDR: CordysAxios) {
  return {
    getCustomerConversionSummary: (data: ReportRangeParams) =>
      CDR.post<CustomerConversionSummaryItem[]>({ url: CustomerConversionSummaryUrl, data }),
    getCustomerConversionDetail: (data: CustomerConversionDetailParams) =>
      CDR.post<CommonList<CustomerConversionDetailItem>>({ url: CustomerConversionDetailUrl, data }),
    exportCustomerConversionDetail: (data: CustomerConversionDetailParams) =>
      CDR.post(
        { url: CustomerConversionDetailExportUrl, data, responseType: 'blob' },
        { isTransformResponse: false, isReturnNativeResponse: true }
      ),
    exportCustomerConversion: (data: ReportRangeParams) =>
      CDR.post(
        { url: CustomerConversionExportUrl, data, responseType: 'blob' },
        { isTransformResponse: false, isReturnNativeResponse: true }
      ),
  };
}
