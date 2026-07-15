import { ToolsRouteEnum } from '@/enums/routeEnum';

import { DEFAULT_LAYOUT } from '../base';
import type { AppRouteRecordRaw } from '../types';

const tools: AppRouteRecordRaw = {
  path: '/tools',
  name: ToolsRouteEnum.TOOLS,
  redirect: '/tools/number-cube',
  component: DEFAULT_LAYOUT,
  meta: {
    locale: 'menu.tools',
    permissions: ['NUMBER_CUBE:READ'],
    icon: 'iconicon_set_up',
    collapsedLocale: 'menu.toolsCollapsed',
  },
  children: [
    {
      path: 'number-cube',
      name: ToolsRouteEnum.TOOLS_NUMBER_CUBE,
      component: () => import('@/views/tools/number-cube/index.vue'),
      meta: {
        locale: 'menu.tools.numberCube',
        permissions: ['NUMBER_CUBE:READ'],
      },
    },
  ],
};

export default tools;
