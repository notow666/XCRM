import { MMBAAuditRouteEnum } from '@/enums/routeEnum';

import { DEFAULT_LAYOUT } from '../base';
import type { AppRouteRecordRaw } from '../types';

const mmbaAudit: AppRouteRecordRaw = {
  path: '/mmba-audit',
  name: MMBAAuditRouteEnum.MMBA_AUDIT,
  redirect: '/mmba-audit/wechat',
  component: DEFAULT_LAYOUT,
  meta: {
    locale: 'menu.mmbaAudit',
    permissions: ['MMBA_AUDIT:READ'],
    icon: 'iconicon_dashboard1',
    hideChildrenInMenu: true,
    collapsedLocale: 'menu.mmbaAuditCollapsed',
  },
  children: [
    {
      path: 'wechat',
      name: MMBAAuditRouteEnum.MMBA_AUDIT_WECHAT,
      component: () => import('../../../views/mmba-audit/index.vue'),
      meta: {
        locale: 'mmbaAudit.channel.wechat',
        isTopMenu: true,
        permissions: ['MMBA_AUDIT:READ'],
      },
    },
    {
      path: 'call',
      name: MMBAAuditRouteEnum.MMBA_AUDIT_CALL,
      component: () => import('../../../views/mmba-audit/index.vue'),
      meta: {
        locale: 'mmbaAudit.channel.call',
        isTopMenu: true,
        permissions: ['MMBA_AUDIT:READ'],
      },
    },
    {
      path: 'sms',
      name: MMBAAuditRouteEnum.MMBA_AUDIT_SMS,
      component: () => import('../../../views/mmba-audit/index.vue'),
      meta: {
        locale: 'mmbaAudit.channel.sms',
        isTopMenu: true,
        permissions: ['MMBA_AUDIT:READ'],
      },
    },
  ],
};

export default mmbaAudit;
