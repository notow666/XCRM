import type { CordysAxios } from '@lib/shared/api/http/Axios';
import { MmbaPhonePreferenceUrl } from '@lib/shared/api/requrls/mmba/phone';
import type { MmbaPhonePreference, MmbaPhonePreferenceUpdateParams } from '@lib/shared/models/mmba/phone';

export default function useMmbaPhoneApi(CDR: CordysAxios) {
  function getMmbaPhonePreference() {
    return CDR.get<MmbaPhonePreference>({ url: MmbaPhonePreferenceUrl });
  }

  function updateMmbaPhonePreference(data: MmbaPhonePreferenceUpdateParams) {
    return CDR.put<MmbaPhonePreference>({ url: MmbaPhonePreferenceUrl, data });
  }

  return {
    getMmbaPhonePreference,
    updateMmbaPhonePreference,
  };
}
