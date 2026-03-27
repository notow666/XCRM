import { showToast } from 'vant';

import { useI18n } from '@lib/shared/hooks/useI18n';

import router from '@/router';
import { WHITE_LIST } from '@/router/constants';
import useAppStore from '@/store/modules/app';
import useUserStore from '@/store/modules/user';

export default function useUser() {
  const { t } = useI18n();

  const logout = async (logoutTo?: string, _noRedirect?: boolean, silence = false) => {
    try {
      const appStore = useAppStore();
      const tenantIdToRedirect = (() => {
        const appTenantId = appStore.tenantId;
        if (typeof appTenantId === 'string' && appTenantId.trim()) return appTenantId;
        const routeTenantId = router.currentRoute.value.params.tenantId;
        return typeof routeTenantId === 'string' && routeTenantId.trim() ? routeTenantId : '';
      })();
      const userStore = useUserStore();
      await userStore.logout();

      // 清空租户相关字段（persist 会把空值写回本地）
      appStore.setTenantId('');
      appStore.setOrgId('');

      if (!silence) {
        showToast(t('message.logoutSuccess'));
        const targetName = logoutTo && typeof logoutTo === 'string' ? logoutTo : 'login';
        if (targetName === 'login' && !tenantIdToRedirect) {
          showToast('请求非法');
          return;
        }
        router.replace({
          name: targetName,
          params: targetName === 'login' ? { tenantId: tenantIdToRedirect } : undefined,
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

  const isWhiteListPage = () => {
    const currentRoute = router.currentRoute.value;
    return WHITE_LIST.some((e) => e.path.includes(currentRoute.path));
  };

  return {
    logout,
    isLoginPage,
    isWhiteListPage,
  };
}
