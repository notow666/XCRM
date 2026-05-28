import { createRouter, createWebHashHistory } from 'vue-router';

import { resolvePersistedAppTenantIdForRedirect } from '@lib/shared/method/tenant-url';

import 'nprogress/nprogress.css';
import createRouteGuard from './guard/index';
import { platformRoutes, tenantRoutes } from './routes';
import { NO_RESOURCE_ROUTE, NOT_FOUND_ROUTE } from './routes/base';
import { createLegacyTenantRedirects, prefixRoutesForTenantScope } from './routes/tenant-route-utils';
import NProgress from 'nprogress';
import type { RouteRecordRaw } from 'vue-router';

NProgress.configure({ showSpinner: false });

const tenantScopedChildren: RouteRecordRaw[] = [
  {
    path: 'login',
    name: 'login',
    component: () => import('@/views/base/login/index.vue'),
    meta: {
      requiresAuth: false,
    },
  },
  ...prefixRoutesForTenantScope(tenantRoutes as RouteRecordRaw[]),
  prefixRoutesForTenantScope([NO_RESOURCE_ROUTE])[0],
];

const router = createRouter({
  history: createWebHashHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: () => {
        const tenantId = resolvePersistedAppTenantIdForRedirect();
        if (tenantId) {
          return `/${tenantId}/login`;
        }
        return '/platform/login';
      },
    },
    {
      path: '/platform/login',
      name: 'platformLogin',
      component: () => import('@/views/platform-login/index.vue'),
      meta: {
        requiresAuth: false,
      },
    },
    {
      path: '/data-specialist/login',
      name: 'dataSpecialistLogin',
      component: () => import('../views/data-specialist/login/index.vue'),
      meta: {
        requiresAuth: false,
      },
    },
    {
      path: '/data-specialist/import',
      name: 'dataSpecialistImport',
      component: () => import('../views/data-specialist/import/index.vue'),
      meta: {
        requiresAuth: false,
      },
    },
    ...platformRoutes,
    ...createLegacyTenantRedirects(),
    {
      path: '/:tenantId',
      children: tenantScopedChildren,
    },
    NOT_FOUND_ROUTE,
  ],
});

createRouteGuard(router);

export default router;
