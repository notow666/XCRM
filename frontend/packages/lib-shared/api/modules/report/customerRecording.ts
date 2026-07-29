import type { CordysAxios } from '@lib/shared/api/http/Axios';
import {
  CustomerRecordingAudioUrl,
  CustomerRecordingEmployeeOptionsUrl,
  CustomerRecordingPageUrl,
} from '@lib/shared/api/requrls/report/customerRecording';
import type { CommonList } from '@lib/shared/models/common';
import type {
  CustomerRecordingEmployeeOption,
  CustomerRecordingListItem,
  CustomerRecordingPageParams,
} from '@lib/shared/models/report/customerRecording';

export default function useCustomerRecordingApi(CDR: CordysAxios) {
  function getCustomerRecordingPage(data: CustomerRecordingPageParams) {
    return CDR.post<CommonList<CustomerRecordingListItem>>({ url: CustomerRecordingPageUrl, data });
  }

  function getCustomerRecordingEmployeeOptions(departmentId?: string) {
    return CDR.get<CustomerRecordingEmployeeOption[]>({
      url: CustomerRecordingEmployeeOptionsUrl,
      params: { departmentId },
    });
  }

  function previewCustomerRecordingAudio(auditId: string) {
    return CDR.get(
      {
        url: `${CustomerRecordingAudioUrl}/${auditId}`,
        responseType: 'blob',
      },
      { isTransformResponse: false, isReturnNativeResponse: true }
    );
  }

  return {
    getCustomerRecordingPage,
    getCustomerRecordingEmployeeOptions,
    previewCustomerRecordingAudio,
  };
}
