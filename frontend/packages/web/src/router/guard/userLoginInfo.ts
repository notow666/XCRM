import { clearToken, hasToken, isLoginExpires } from '@lib/shared/method/auth';

import { platformIsLogin } from '@/api/modules';
import useUser from '@/hooks/useUser';
import useUserStore from '@/store/modules/user';

import NProgress from 'nprogress';
import type { LocationQueryRaw, Router } from 'vue-router';

export default function setupUserLoginInfoGuard(router: Router) {
  router.beforeEach(async (to, from, next) => {
    NProgress.start();

    const { isWhiteListPage } = useUser();
    // 登录过期清除token
    if (isLoginExpires()) {
      clearToken();
    }

    const tokenExists = hasToken();
    const userStore = useUserStore();
    let isPlatformUser = userStore.userInfo.source === 'PLATFORM';
    const isPlatformRoute = to.path.startsWith('/platform');
    const isManagementCenterRoute = to.path.startsWith('/management-center');

    if (tokenExists && (isPlatformRoute || isManagementCenterRoute) && !isPlatformUser) {
      try {
        const platformUser = await platformIsLogin();
        if (platformUser?.source === 'PLATFORM') {
          userStore.setInfo(platformUser as any);
          isPlatformUser = true;
        }
      } catch (error) {
        // ignore and fallback to existing guard branches below
      }
    }

    // 未登录访问受限页面重定向登录页
    if (!tokenExists && to.name !== 'login' && to.name !== 'platformLogin' && !isWhiteListPage()) {
      if (isPlatformRoute) {
        next({ name: 'platformLogin' });
        NProgress.done();
        return;
      }
      const routeTenantId = to.params?.tenantId;
      const appTenantId = userStore.userInfo?.tenantId || '';
      const tenantIdToRedirect =
        (typeof routeTenantId === 'string' && routeTenantId.trim()) ||
        (typeof appTenantId === 'string' && appTenantId.trim()) ||
        'default';
      next({
        name: 'login',
        params: {
          tenantId: tenantIdToRedirect,
        },
        query: {
          redirect: to.name,
          ...to.query,
        } as LocationQueryRaw,
      });
      NProgress.done();
      return;
    }

    // 已登录访问 login重定向（有权限第一个页面）
    if (to.name === 'login' && tokenExists) {
      next({ name: isPlatformUser ? 'managementCenterOverview' : 'workbenchIndex' });
      NProgress.done();
      return;
    }

    if (to.name === 'platformLogin' && tokenExists) {
      next({ name: 'managementCenterOverview' });
      NProgress.done();
      return;
    }

    if (isPlatformUser && !isPlatformRoute && !isManagementCenterRoute) {
      next({ name: 'managementCenterOverview' });
      NProgress.done();
      return;
    }

    // 其他情况（放行：已登录访问正常页面\未登录访问白名单页面）
    next();
    NProgress.done();
  });
}
