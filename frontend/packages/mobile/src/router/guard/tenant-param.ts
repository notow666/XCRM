import { hasToken } from '@lib/shared/method/auth';
import { getSessionTenantId, isTenantRouteMismatch, normalizeTenantId } from '@lib/shared/method/tenant-url';

import useAppStore from '@/store/modules/app';
import useUserStore from '@/store/modules/user';

import type { Router } from 'vue-router';

function isTenantBoundUser(source: string | undefined): boolean {
  return !!source && source !== 'PLATFORM' && source !== 'DATA_SPECIALIST';
}

/** 已登录时同步 appStore 租户；登录路由 params 与会话不一致时纠正 */
export default function setupTenantParamGuard(router: Router) {
  router.beforeEach((to, _from, next) => {
    if (!hasToken()) {
      next();
      return;
    }

    const userStore = useUserStore();
    const appStore = useAppStore();
    const sessionTenantId = getSessionTenantId() || normalizeTenantId(userStore.userInfo?.tenantId);

    if (!sessionTenantId || !isTenantBoundUser(userStore.userInfo?.source)) {
      next();
      return;
    }

    appStore.setTenantId(sessionTenantId);

    const routeTenantId = normalizeTenantId(to.params.tenantId);
    if (routeTenantId && isTenantRouteMismatch(routeTenantId, sessionTenantId)) {
      if (to.name === 'login') {
        next({ name: 'login', params: { tenantId: sessionTenantId }, replace: true });
        return;
      }
    }

    next();
  });
}
