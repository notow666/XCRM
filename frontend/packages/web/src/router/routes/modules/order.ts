// 订单模块本期仅隐藏前端入口，保留页面、接口和历史数据，便于后续恢复。
// import { OrderRouteEnum } from '@/enums/routeEnum';
//
// import { DEFAULT_LAYOUT } from '../base';
// import type { AppRouteRecordRaw } from '../types';
//
// const order: AppRouteRecordRaw = {
//   path: '/order',
//   name: OrderRouteEnum.ORDER,
//   redirect: '/order/index',
//   component: DEFAULT_LAYOUT,
//   meta: {
//     locale: 'module.order',
//     permissions: ['ORDER:READ'],
//     icon: 'iconicon_order_form',
//     hideChildrenInMenu: true,
//     collapsedLocale: 'module.order',
//   },
//   children: [
//     {
//       path: 'index',
//       name: OrderRouteEnum.ORDER_INDEX,
//       component: () => import('@/views/order/order/index.vue'),
//       meta: {
//         locale: 'module.order',
//         isTopMenu: true,
//         permissions: ['ORDER:READ'],
//       },
//     },
//   ],
// };
//
// export default order;
