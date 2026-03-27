import { createRouter, createWebHashHistory } from 'vue-router';

import 'nprogress/nprogress.css';
import createRouteGuard from './guard/index';
import appRoutes from './routes';
import { NO_RESOURCE_ROUTE, NOT_FOUND_ROUTE } from './routes/base';
import NProgress from 'nprogress';

NProgress.configure({ showSpinner: false });

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      redirect: () => {
        try {
          const appRaw = localStorage.getItem('app');
          if (appRaw) {
            const app = JSON.parse(appRaw);
            const tenantId = typeof app?.tenantId === 'string' ? app.tenantId.trim() : '';
            if (tenantId) {
              return `/${tenantId}/login`;
            }
          }
        } catch {
          // ignore
        }
        return '/platform/login';
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
    {
      path: '/platform/login',
      name: 'platformLogin',
      component: () => import('@/views/platform-login/index.vue'),
      meta: {
        requiresAuth: false,
      },
    },
    ...appRoutes,
    NOT_FOUND_ROUTE,
    NO_RESOURCE_ROUTE,
  ],
});

createRouteGuard(router);

export default router;
