import { MMBAAuditRouteEnum } from '@/enums/routeEnum';

import { DEFAULT_LAYOUT } from '../base';
import type { AppRouteRecordRaw } from '../types';

const mmbaAudit: AppRouteRecordRaw = {
  path: '/mmba-audit',
  name: MMBAAuditRouteEnum.MMBA_AUDIT,
  redirect: '/mmba-audit/mgmt-sso',
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
      path: 'mgmt-sso',
      name: MMBAAuditRouteEnum.MMBA_AUDIT_MGMT_SSO,
      component: () => import('../../../views/mmba-audit/mgmt-sso-portal.vue'),
      meta: {
        locale: 'menu.mmbaAudit',
        permissions: ['MMBA_AUDIT:READ'],
      },
    },
    {
      path: 'wechat',
      name: MMBAAuditRouteEnum.MMBA_AUDIT_WECHAT,
      component: () => import('../../../views/mmba-audit/index.vue'),
      meta: {
        locale: 'mmbaAudit.channel.wechat',
        hideInMenu: true,
        permissions: ['MMBA_AUDIT:READ'],
      },
    },
    {
      path: 'call',
      name: MMBAAuditRouteEnum.MMBA_AUDIT_CALL,
      component: () => import('../../../views/mmba-audit/index.vue'),
      meta: {
        locale: 'mmbaAudit.channel.call',
        hideInMenu: true,
        permissions: ['MMBA_AUDIT:READ'],
      },
    },
    {
      path: 'sms',
      name: MMBAAuditRouteEnum.MMBA_AUDIT_SMS,
      component: () => import('../../../views/mmba-audit/index.vue'),
      meta: {
        locale: 'mmbaAudit.channel.sms',
        hideInMenu: true,
        permissions: ['MMBA_AUDIT:READ'],
      },
    },
  ],
};

export default mmbaAudit;
