import { ManagementCenterRouteEnum } from '@/enums/routeEnum';

import { DEFAULT_LAYOUT } from '../base';
import type { AppRouteRecordRaw } from '../types';

const managementCenter: AppRouteRecordRaw = {
  path: '/management-center',
  name: ManagementCenterRouteEnum.MANAGEMENT_CENTER,
  redirect: '/management-center/overview',
  component: DEFAULT_LAYOUT,
  meta: {
    locale: 'menu.managementCenter',
    permissions: ['PLATFORM_ADMIN:READ'],
    icon: 'iconicon_set_up',
    collapsedLocale: 'menu.managementCenter',
  },
  children: [
    {
      path: 'overview',
      name: ManagementCenterRouteEnum.MANAGEMENT_CENTER_OVERVIEW,
      component: () => import('@/views/management-center/overview/index.vue'),
      meta: {
        locale: 'menu.managementCenter.overview',
        permissions: ['PLATFORM_ADMIN:READ'],
      },
    },
    {
      path: 'tenant',
      name: ManagementCenterRouteEnum.MANAGEMENT_CENTER_TENANT,
      component: () => import('@/views/management-center/tenant/index.vue'),
      meta: {
        locale: 'menu.managementCenter.tenant',
        permissions: ['PLATFORM_ADMIN:READ'],
      },
    },
    {
      path: 'audit',
      name: ManagementCenterRouteEnum.MANAGEMENT_CENTER_AUDIT,
      component: () => import('@/views/management-center/audit/index.vue'),
      meta: {
        locale: 'menu.managementCenter.audit',
        permissions: ['PLATFORM_ADMIN:READ'],
      },
    },
  ],
};

export default managementCenter;
