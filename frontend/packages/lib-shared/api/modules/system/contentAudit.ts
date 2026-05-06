import type { CordysAxios } from '@lib/shared/api/http/Axios';
import { GetWechatAccountStatPageUrl } from '@lib/shared/api/requrls/system/contentAudit';
import type { CommonList } from '@lib/shared/models/common';
import type { WechatAccountStatItem, WechatAccountStatTableParams } from '@lib/shared/models/system/contentAudit';

export default function useContentAuditApi(CDR: CordysAxios) {
  function getWechatAccountStatPage(data: WechatAccountStatTableParams) {
    return CDR.post<CommonList<WechatAccountStatItem>>({ url: GetWechatAccountStatPageUrl, data });
  }

  return {
    getWechatAccountStatPage,
  };
}
