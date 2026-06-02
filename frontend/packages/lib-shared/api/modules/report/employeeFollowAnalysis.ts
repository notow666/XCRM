import type { CordysAxios } from '@lib/shared/api/http/Axios';
import {
  EmployeeFollowAnalysisDrilldownUrl,
  EmployeeFollowAnalysisExportUrl,
  EmployeeFollowAnalysisSummaryUrl,
} from '@lib/shared/api/requrls/report/employeeFollowAnalysis';
import type { CommonList } from '@lib/shared/models/common';
import type {
  EmployeeFollowAnalysisDrilldownItem,
  EmployeeFollowAnalysisDrilldownParams,
  EmployeeFollowAnalysisSummaryItem,
  EmployeeFollowAnalysisSummaryParams,
} from '@lib/shared/models/report/employeeFollowAnalysis';

export default function useEmployeeFollowAnalysisApi(CDR: CordysAxios) {
  function getEmployeeFollowAnalysisSummary(data: EmployeeFollowAnalysisSummaryParams) {
    return CDR.post<EmployeeFollowAnalysisSummaryItem[]>({ url: EmployeeFollowAnalysisSummaryUrl, data });
  }

  function getEmployeeFollowAnalysisDrilldown(data: EmployeeFollowAnalysisDrilldownParams) {
    return CDR.post<CommonList<EmployeeFollowAnalysisDrilldownItem>>({ url: EmployeeFollowAnalysisDrilldownUrl, data });
  }

  function exportEmployeeFollowAnalysisSummary(data: EmployeeFollowAnalysisSummaryParams) {
    return CDR.post(
      { url: EmployeeFollowAnalysisExportUrl, data, responseType: 'blob' },
      { isTransformResponse: false, isReturnNativeResponse: true }
    );
  }

  return {
    getEmployeeFollowAnalysisSummary,
    getEmployeeFollowAnalysisDrilldown,
    exportEmployeeFollowAnalysisSummary,
  };
}
