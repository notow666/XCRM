import { TaskRouteEnum } from '@/enums/routeEnum';

import { DEFAULT_LAYOUT } from '../base';
import type { AppRouteRecordRaw } from '../types';

const task: AppRouteRecordRaw = {
  path: '/task',
  name: TaskRouteEnum.TASK,
  redirect: '/task/index',
  component: DEFAULT_LAYOUT,
  meta: {
    locale: 'module.task',
    permissions: ['TASK:READ'],
    icon: 'iconicon_data_plan',
    hideChildrenInMenu: true,
    collapsedLocale: 'module.task',
  },
  children: [
    {
      path: 'index',
      name: TaskRouteEnum.TASK_INDEX,
      component: () => import('@/views/task/index.vue'),
      meta: {
        locale: 'module.task',
        isTopMenu: true,
        permissions: ['TASK:READ'],
      },
    },
  ],
};

export default task;
