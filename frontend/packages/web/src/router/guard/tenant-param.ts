import { hasToken } from '@lib/shared/method/auth';
import {
  getSessionTenantId,
  isTenantRouteMismatch,
  isUsableBusinessTenantId,
  normalizeTenantId,
  resolvePersistedAppTenantIdForRedirect,
} from '@lib/shared/method/tenant-url';

import { isTenantScopedRoute } from '@/router/routes/tenant-route-utils';
import useAppStore from '@/store/modules/app';
import useUserStore from '@/store/modules/user';

import type { NavigationGuardNext, RouteLocationNormalized, Router } from 'vue-router';

const GLOBAL_ROUTE_PREFIXES = ['/platform', '/data-specialist', '/management-center'];

function isGlobalRoute(path: string): boolean {
  return GLOBAL_ROUTE_PREFIXES.some((prefix) => path === prefix || path.startsWith(`${prefix}/`));
}

function isTenantBoundUser(source: string | undefined): boolean {
  return source !== 'PLATFORM' && source !== 'DATA_SPECIALIST';
}

function resolveTenantIdForNavigation(toParams: Record<string, unknown>, fromParams: Record<string, unknown>): string {
  const fromTo = normalizeTenantId(toParams.tenantId);
  if (isUsableBusinessTenantId(fromTo)) {
    return fromTo;
  }
  const fromPrev = normalizeTenantId(fromParams.tenantId);
  if (isUsableBusinessTenantId(fromPrev)) {
    return fromPrev;
  }
  return resolvePersistedAppTenantIdForRedirect();
}

function redirectWithSessionTenant(to: RouteLocationNormalized, sessionTenantId: string, next: NavigationGuardNext) {
  if (to.name != null && typeof to.name === 'string') {
    next({
      name: to.name,
      params: { ...to.params, tenantId: sessionTenantId },
      query: to.query,
      hash: to.hash,
      replace: true,
    });
    return;
  }
  const correctedPath = to.path.replace(/^\/[^/]+/, `/${sessionTenantId}`);
  next({
    path: correctedPath,
    query: to.query,
    hash: to.hash,
    replace: true,
  });
}

/** 为 /:tenantId/* 命名路由补全 tenantId，并在进入租户域时同步 appStore */
export default function setupTenantParamGuard(router: Router) {
  router.beforeEach((to, from, next) => {
    const appStore = useAppStore();

    if (isGlobalRoute(to.path) || isGlobalRoute(from.path)) {
      next();
      return;
    }

    const needsTenant = to.matched.some((record) => record.path.includes(':tenantId'));
    const userStore = useUserStore();
    const sessionTenantId = hasToken() ? getSessionTenantId() || normalizeTenantId(userStore.userInfo?.tenantId) : '';

    if (needsTenant && hasToken() && sessionTenantId && isTenantBoundUser(userStore.userInfo?.source)) {
      if (isTenantRouteMismatch(to.params.tenantId, sessionTenantId)) {
        redirectWithSessionTenant(to, sessionTenantId, next);
        return;
      }
      appStore.setTenantId(sessionTenantId);
      next();
      return;
    }

    const tenantId = resolveTenantIdForNavigation(
      to.params as Record<string, unknown>,
      from.params as Record<string, unknown>
    );

    if (needsTenant && isUsableBusinessTenantId(tenantId)) {
      appStore.setTenantId(tenantId);
      if (normalizeTenantId(to.params.tenantId) !== tenantId && to.name != null && typeof to.name === 'string') {
        next({
          name: to.name,
          params: { ...to.params, tenantId },
          query: to.query,
          hash: to.hash,
          replace: to.path === from.path,
        });
        return;
      }
    } else if (isTenantScopedRoute(to.path, to.params as Record<string, unknown>)) {
      appStore.setTenantId(normalizeTenantId(to.params.tenantId));
    }

    next();
  });
}
