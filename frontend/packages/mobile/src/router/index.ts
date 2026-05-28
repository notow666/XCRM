import { createRouter, createWebHashHistory } from 'vue-router';

import { resolvePersistedAppTenantIdForRedirect } from '@lib/shared/method/tenant-url';

import 'nprogress/nprogress.css';
import createRouteGuard from './guard/index';
import appRoutes from './routes';
import { AUTH_DISABLED_ROUTE, AUTH_LOGIN_LOADING_ROUTE, NO_RESOURCE_ROUTE } from './routes/base';
import NProgress from 'nprogress';

NProgress.configure({ showSpinner: false });

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      redirect: () => {
        const tenantId = resolvePersistedAppTenantIdForRedirect();
        return tenantId ? `/${tenantId}/login` : '/default/login';
      },
    },
    {
      path: '/:tenantId/login',
      name: 'login',
      component: () => import('@/views/base/login/index.vue'),
      meta: {
        requiresAuth: false,
      },
    },
    ...appRoutes,
    NO_RESOURCE_ROUTE,
    AUTH_DISABLED_ROUTE,
    AUTH_LOGIN_LOADING_ROUTE,
  ],
});

createRouteGuard(router);

export default router;
