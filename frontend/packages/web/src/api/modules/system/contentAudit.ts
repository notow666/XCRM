import { GetWechatAccountStatPageUrl } from '@lib/shared/api/requrls/system/contentAudit';
import type { CommonList } from '@lib/shared/models/common';
import type { WechatAccountStatItem, WechatAccountStatTableParams } from '@lib/shared/models/system/contentAudit';

import CDR from '@/api/http/index';

export function getWechatAccountStatPage(data: WechatAccountStatTableParams) {
  return CDR.post<CommonList<WechatAccountStatItem>>({ url: GetWechatAccountStatPageUrl, data });
}
