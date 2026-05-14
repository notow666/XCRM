import type { CordysAxios } from '@lib/shared/api/http/Axios';
import { LocaleChangeUrl } from '@lib/shared/api/requrls/sys';

export default function useSysApi(CDR: CordysAxios) {
  function changeLocaleBackEnd(language: string) {
    return CDR.post({ url: LocaleChangeUrl, data: { language } });
  }

  return {
    changeLocaleBackEnd,
  };
}
