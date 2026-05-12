import { ReportRouteEnum } from '@/enums/routeEnum';

import { DEFAULT_LAYOUT } from '../base';
import type { AppRouteRecordRaw } from '../types';

const report: AppRouteRecordRaw = {
  path: '/report',
  name: ReportRouteEnum.REPORT,
  redirect: '/report/employee/follow-up-analysis',
  component: DEFAULT_LAYOUT,
  meta: {
    locale: 'menu.report',
    icon: 'iconicon_data_plan',
    collapsedLocale: 'menu.reportCollapsed',
  },
  children: [
    {
      path: 'employee',
      name: ReportRouteEnum.REPORT_EMPLOYEE,
      redirect: '/report/employee/follow-up-analysis',
      component: () => import('@/views/report/route-outlet.vue'),
      meta: {
        locale: 'menu.report.employee',
      },
      children: [
        {
          path: 'follow-up-analysis',
          name: ReportRouteEnum.REPORT_EMPLOYEE_FOLLOW_UP,
          component: () => import('@/views/report/employee-follow-up/index.vue'),
          meta: {
            locale: 'menu.report.employeeFollowUp',
          },
        },
      ],
    },
    {
      path: 'contract',
      name: ReportRouteEnum.REPORT_CONTRACT,
      component: () => import('@/views/report/contract-analysis/index.vue'),
      meta: {
        locale: 'menu.report.contract',
      },
    },
  ],
};

export default report;
