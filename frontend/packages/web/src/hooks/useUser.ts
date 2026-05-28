import { useI18n } from '@lib/shared/hooks/useI18n';

import { dataSpecialistLogout, platformLogout } from '@/api/modules';
import router from '@/router';
import { WHITE_LIST_NAME } from '@/router/constants';
import useAppStore from '@/store/modules/app';
import useUserStore from '@/store/modules/user';

import useDiscreteApi from './useDiscreteApi';

export default function useUser() {
  const { t } = useI18n();

  const logout = async (logoutTo?: string, noRedirect?: boolean, silence = false) => {
    try {
      const appStore = useAppStore();
      const userStore = useUserStore();
      const tenantIdToRedirect = (() => {
        const userTenantId = userStore.userInfo?.tenantId;
        if (typeof userTenantId === 'string' && userTenantId.trim()) return userTenantId;
        const appTenantId = appStore.tenantId;
        if (typeof appTenantId === 'string' && appTenantId.trim()) return appTenantId;
        const routeTenantId = router.currentRoute.value.params.tenantId;
        return typeof routeTenantId === 'string' && routeTenantId.trim() ? routeTenantId : '';
      })();
      const isPlatformUser = userStore.userInfo.source === 'PLATFORM';
      const isDataSpecialist = userStore.userInfo.source === 'DATA_SPECIALIST';
      if (isPlatformUser) {
        try {
          await platformLogout();
        } catch {
          // 服务端登出失败仍清理本地会话并跳转
        } finally {
          userStore.logoutCallBack();
        }
      } else if (isDataSpecialist) {
        try {
          await dataSpecialistLogout();
        } catch {
          // 服务端登出失败仍清理本地会话并跳转
        } finally {
          userStore.logoutCallBack();
        }
      } else {
        try {
          await userStore.logout(silence);
        } catch {
          // userStore.logout 的 finally 已执行 logoutCallBack；忽略 signout 失败
        }
      }

      // 登出后保留当前租户ID，避免登录页 /get-key、/is-login 请求头丢失租户
      appStore.setTenantId(tenantIdToRedirect);
      appStore.setOrgId('');

      const { message } = useDiscreteApi();
      const currentRoute = router.currentRoute.value;
      if (!silence) {
        message.success(t('message.logoutSuccess'));
        let targetName = 'login';
        if (logoutTo && typeof logoutTo === 'string') {
          targetName = logoutTo;
        } else if (isDataSpecialist) {
          targetName = 'dataSpecialistLogin';
        } else if (isPlatformUser) {
          targetName = 'platformLogin';
        }
        if (targetName === 'login' && !tenantIdToRedirect) {
          message.error(t('message.logoutFail') || '无法识别当前租户，请返回对应租户登录页');
          return;
        }
        await router.push({
          name: targetName,
          params: targetName === 'login' ? { tenantId: tenantIdToRedirect } : undefined,
          query:
            noRedirect || targetName === 'dataSpecialistLogin' || targetName === 'platformLogin'
              ? {}
              : {
                  ...router.currentRoute.value.query,
                  redirect: currentRoute.name as string,
                },
        });
      }
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
    }
  };

  const isLoginPage = () => {
    return window.location.hash.indexOf('login') > -1;
  };

  const isWhiteListPage = (route = router.currentRoute.value) => {
    const { name } = route;
    if (name == null || typeof name !== 'string') {
      return false;
    }
    return WHITE_LIST_NAME.includes(name);
  };

  const goUserHasPermissionPage = () => {
    const { redirect, ...othersQuery } = router.currentRoute.value.query;
    const userStore = useUserStore();
    const isPlatformUser = userStore.userInfo.source === 'PLATFORM';

    router.push({
      name: isPlatformUser ? 'managementCenterOverview' : 'workbenchIndex',
      query: {
        ...othersQuery,
      },
    });
  };

  return {
    logout,
    isLoginPage,
    isWhiteListPage,
    goUserHasPermissionPage,
  };
}
