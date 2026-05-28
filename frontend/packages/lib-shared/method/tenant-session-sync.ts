import { hasToken } from './auth';
import { getSessionTenantId, isTenantRouteMismatch } from './tenant-url';

/** localStorage 事件键：登出或会话租户变更时通知其它标签页 */
export const TENANT_SESSION_SYNC_KEY = 'crm-tenant-session-sync';

export function broadcastTenantSessionSync(reason: 'logout' | 'login' = 'logout') {
  if (typeof window === 'undefined') {
    return;
  }
  try {
    localStorage.setItem(TENANT_SESSION_SYNC_KEY, `${reason}:${Date.now()}`);
  } catch {
    // ignore quota / private mode
  }
}

type RouterLike = {
  currentRoute: { value: { params: Record<string, unknown>; path: string; name?: string | symbol | null } };
  replace: (location: Record<string, unknown>) => Promise<unknown>;
};

/**
 * 其它标签页登出或租户变更时，将当前页纠正到会话租户或跳转登录。
 */
export function setupTenantSessionTabSync(router: RouterLike) {
  if (typeof window === 'undefined') {
    return;
  }

  window.addEventListener('storage', (event) => {
    if (event.key !== TENANT_SESSION_SYNC_KEY || !event.newValue) {
      return;
    }

    if (event.newValue.startsWith('logout:')) {
      if (hasToken()) {
        const tenantId = getSessionTenantId();
        window.location.replace(tenantId ? `#/${tenantId}/login` : '#/platform/login');
        window.location.reload();
      }
      return;
    }

    if (!hasToken()) {
      return;
    }

    const sessionTenantId = getSessionTenantId();
    if (!sessionTenantId) {
      return;
    }

    const routeTenantId = router.currentRoute.value.params?.tenantId;
    if (!isTenantRouteMismatch(routeTenantId, sessionTenantId)) {
      return;
    }

    const routeName = router.currentRoute.value.name;
    if (routeName != null && typeof routeName === 'string') {
      void router.replace({
        name: routeName,
        params: { ...router.currentRoute.value.params, tenantId: sessionTenantId },
        replace: true,
      });
      return;
    }

    const correctedPath = router.currentRoute.value.path.replace(/^\/[^/]+/, `/${sessionTenantId}`);
    void router.replace({ path: correctedPath, replace: true });
  });
}

export function syncAppStoreTenantFromSession(setTenantId: (id: string) => void) {
  const sessionTenantId = getSessionTenantId();
  if (sessionTenantId) {
    setTenantId(sessionTenantId);
  }
}
