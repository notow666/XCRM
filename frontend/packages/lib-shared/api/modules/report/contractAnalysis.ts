import type { CordysAxios } from '@lib/shared/api/http/Axios';
import {
  ContractAnalysisDetailExportUrl,
  ContractAnalysisDetailUrl,
  ContractAnalysisExportUrl,
  ContractAnalysisSummaryUrl,
} from '@lib/shared/api/requrls/report/contractAnalysis';
import type { CommonList } from '@lib/shared/models/common';
import type {
  ContractAnalysisDetailItem,
  ContractAnalysisDetailParams,
  ContractAnalysisSummaryItem,
  ReportRangeParams,
} from '@lib/shared/models/report/contractAnalysis';

export default function useContractAnalysisApi(CDR: CordysAxios) {
  return {
    getContractAnalysisSummary: (data: ReportRangeParams) =>
      CDR.post<ContractAnalysisSummaryItem[]>({ url: ContractAnalysisSummaryUrl, data }),
    getContractAnalysisDetail: (data: ContractAnalysisDetailParams) =>
      CDR.post<CommonList<ContractAnalysisDetailItem>>({ url: ContractAnalysisDetailUrl, data }),
    exportContractAnalysisDetail: (data: ContractAnalysisDetailParams) =>
      CDR.post(
        { url: ContractAnalysisDetailExportUrl, data, responseType: 'blob' },
        { isTransformResponse: false, isReturnNativeResponse: true }
      ),
    exportContractAnalysis: (data: ReportRangeParams) =>
      CDR.post(
        { url: ContractAnalysisExportUrl, data, responseType: 'blob' },
        { isTransformResponse: false, isReturnNativeResponse: true }
      ),
  };
}
