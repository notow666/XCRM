import type { CordysAxios } from '@lib/shared/api/http/Axios';
import { MmbaMgmtSsoRedirectUrl } from '@lib/shared/api/requrls/mmba/mgmtSso';

export interface MmbaMgmtSsoRedirectDTO {
  url: string;
}

export default function useMmbaMgmtSsoApi(CDR: CordysAxios) {
  function getMmbaMgmtSsoRedirectUrl() {
    return CDR.get<MmbaMgmtSsoRedirectDTO>({ url: MmbaMgmtSsoRedirectUrl });
  }

  return {
    getMmbaMgmtSsoRedirectUrl,
  };
}
