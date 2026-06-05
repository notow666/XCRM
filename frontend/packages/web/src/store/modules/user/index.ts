import { defineStore } from 'pinia';

import { CompanyTypeEnum } from '@lib/shared/enums/commonEnum';
import { SystemMessageTypeEnum } from '@lib/shared/enums/systemEnum';
import { useI18n } from '@lib/shared/hooks/useI18n';
import { getGenerateId } from '@lib/shared/method';
import { clearToken, setToken } from '@lib/shared/method/auth';
import { removeRouteListener } from '@lib/shared/method/route-listener';
import { removeScript } from '@lib/shared/method/scriptLoader';
import { broadcastTenantSessionSync } from '@lib/shared/method/tenant-session-sync';
import { resolveTenantIdForAuthRedirect } from '@lib/shared/method/tenant-url';
import type { ApiKeyItem } from '@lib/shared/models/system/business';
import type { LoginParams } from '@lib/shared/models/system/login';
import type { MessageCenterItem } from '@lib/shared/models/system/message';
import type { UserInfo } from '@lib/shared/models/user';

import PlatformAnnouncementNotify from '@/components/business/crm-platform-announcement-notify/index.vue';
import PlatformForceLogoutNotify from '@/components/business/crm-platform-force-logout-notify/index.vue';
import NotifyContent from '@/views/system/message/components/notifyContent.vue';
import NotifyToastContent from '@/views/system/message/components/notifyToastContent.vue';

import { dataSpecialistLogout, getApiKeyList, isLogin, login, signout } from '@/api/modules';
import useDiscreteApi from '@/hooks/useDiscreteApi';
import router from '@/router';
import { WHITE_LIST_NAME } from '@/router/constants';
import useLicenseStore from '@/store/modules/setting/license';
import useAppStoreAccessor from '@/store/storeAccessors';
import { hasAnyPermission } from '@/utils/permission';

import { dispatchOpenMessageDrawer } from '@/constants/messageNotify';
import type { NotificationOptions, NotificationReactive } from 'naive-ui';

const { notification } = useDiscreteApi();

export interface UserState {
  loginType: string[];
  userInfo: UserInfo;
  clientIdRandomId: string; // 客户端随机id
  notify: NotificationReactive | null;
  platformNotify: NotificationReactive | null;
  apiKeyList: ApiKeyItem[];
}

const useUserStore = defineStore('user', {
  persist: true,
  state: (): UserState => ({
    loginType: [],
    userInfo: {
      id: '',
      name: '',
      email: '',
      password: '',
      enable: true,
      createTime: 0,
      updateTime: 0,
      language: '',
      tenantId: '',
      tenantName: '',
      tenantIds: [],
      lastOrganizationId: '',
      phone: '',
      source: '',
      createUser: '',
      updateUser: '',
      platformInfo: '',
      avatar: '',
      permissionIds: [],
      organizationIds: [],
      csrfToken: '',
      sessionId: '',
      roles: [],
      departmentId: '',
      departmentName: '',
      defaultPwd: true,
    },
    clientIdRandomId: '',
    notify: null,
    platformNotify: null,
    apiKeyList: [],
  }),

  getters: {
    isAdmin(state: UserState) {
      return state.userInfo.id === 'admin';
    },
    getScopedValue(state: UserState) {
      const hasAllScopedData = state.userInfo.roles.some((e: any) => e?.dataScope === 'ALL');
      const hasDepScopedData = state.userInfo.roles.some(
        (e: any) => e?.dataScope === 'DEPT_AND_CHILD' || e.dataScope === 'DEPT_CUSTOM'
      );
      if (hasAllScopedData || this.isAdmin) {
        return 'ALL';
      }
      if (hasDepScopedData) {
        return 'DEPARTMENT';
      }
      return 'SELF';
    },
  },
  actions: {
    // 设置用户信息
    setInfo(info: UserInfo) {
      this.$patch({ userInfo: info });
    },
    async login(params: LoginParams) {
      try {
        const res = await login(params);
        setToken(res.sessionId, res.csrfToken);
        this.setInfo(res);
        const appStore = useAppStoreAccessor();
        appStore.setTenantId(res.tenantId || '');
        const lastOrganizationId = res.lastOrganizationId ?? res.organizationIds[0] ?? '';
        this.clientIdRandomId = getGenerateId();
        appStore.setOrgId(lastOrganizationId);
      } catch (error) {
        clearToken();
        throw error;
      }
    },
    // 登出回调
    logoutCallBack() {
      const appStore = useAppStoreAccessor();
      const licenseStore = useLicenseStore();
      if (!licenseStore.hasLicense()) {
        appStore.resetPageConfig();
      }
      appStore.disconnectSystemMessageSSE();
      appStore.resetMessageNotifyState();
      this.destroySystemNotify();
      this.destroyPlatformNotify();
      // 重置用户信息
      this.$reset();
      clearToken();

      removeRouteListener();
      removeScript(CompanyTypeEnum.SQLBot);
      appStore.hideLoading();
      broadcastTenantSessionSync('logout');
      // 登出跳转与租户信息清理由 useUser().logout() 统一处理
    },
    // 登出
    async logout(silence = false) {
      try {
        const { t } = useI18n();
        if (!silence) {
          const appStore = useAppStoreAccessor();
          appStore.showLoading(t('message.loggingOut'));
        }
        await signout();
      } finally {
        this.logoutCallBack();
      }
    },
    // 获取登录认证方式
    async getAuthentication() {
      try {
        // const res = await getAuthenticationList();
        this.loginType = [];
      } catch (error) {
        // eslint-disable-next-line no-console
        console.log(error);
      }
    },
    qrCodeLogin(res: UserInfo) {
      try {
        if (!res) {
          return false;
        }
        setToken(res.sessionId, res.csrfToken);
        this.setInfo(res);
        const appStore = useAppStoreAccessor();
        appStore.setTenantId(res.tenantId || '');
        const lastOrganizationId = res.lastOrganizationId ?? res.organizationIds?.[0] ?? '';
        appStore.setOrgId(lastOrganizationId);
        this.clientIdRandomId = getGenerateId();
        return true;
      } catch (err) {
        // eslint-disable-next-line no-console
        console.log(err);
        clearToken();
        return false;
      }
    },
    async isLogin(isDisabledErrorTip = false) {
      try {
        const res = await isLogin(isDisabledErrorTip);
        if (!res) {
          return false;
        }
        setToken(res.sessionId, res.csrfToken);
        this.setInfo(res);
        const appStore = useAppStoreAccessor();
        appStore.setTenantId(res.tenantId || '');
        const lastOrganizationId = res.lastOrganizationId ?? res.organizationIds?.[0] ?? '';
        appStore.setOrgId(lastOrganizationId);
        if (!this.clientIdRandomId) {
          this.clientIdRandomId = getGenerateId();
        }
        return true;
      } catch (err) {
        // eslint-disable-next-line no-console
        console.log(err);
        return false;
      }
    },
    async checkIsLogin(isDisabledErrorTip = false) {
      const isLoginStatus = await this.isLogin(isDisabledErrorTip);
      const isLoginPage = () => {
        const { name } = router.currentRoute.value;
        if (name == null || typeof name !== 'string') {
          return false;
        }
        return WHITE_LIST_NAME.includes(name);
      };
      if (isLoginStatus) {
        if (isLoginPage()) {
          const appStore = useAppStoreAccessor();
          const isPlatformUser = this.userInfo.source === 'PLATFORM';
          const tenantId = resolveTenantIdForAuthRedirect({
            routeTenantId: router.currentRoute.value.params?.tenantId,
            userTenantId: this.userInfo?.tenantId,
            appTenantId: appStore.tenantId,
            sessionFirst: true,
          });
          await router.push({
            name: isPlatformUser ? 'managementCenterOverview' : 'workbenchIndex',
            params: isPlatformUser || !tenantId ? {} : { tenantId },
          });
        }
      } else if (!isLoginPage()) {
        const appStore = useAppStoreAccessor();
        const tenantId = resolveTenantIdForAuthRedirect({
          routeTenantId: router.currentRoute.value.params?.tenantId,
          userTenantId: this.userInfo?.tenantId,
          appTenantId: appStore.tenantId,
        });
        if (tenantId) {
          router.push({ name: 'login', params: { tenantId } });
        } else {
          router.push({ name: 'platformLogin' });
        }
      }
    },
    // 展示系统公告（持久弹窗）
    showSystemNotify() {
      const appStore = useAppStoreAccessor();
      if (appStore.messageInfo.announcementDTOList?.length) {
        this.notify = notification.create({
          title: '',
          content: () => {
            return h(NotifyContent, {
              onClose: () => this.destroySystemNotify(),
            });
          },
          duration: undefined,
          maxCount: 1,
        } as NotificationOptions);
      }
    },
    /** 业务系统通知：短 Toast，同一会话同 id 不重复弹 */
    showNotificationToasts(items: MessageCenterItem[]) {
      if (!items.length) return;
      const { t } = useI18n();
      const displayItems = items.slice(0, 3);

      displayItems.forEach((item) => {
        const title =
          item.type === SystemMessageTypeEnum.SYSTEM_NOTICE
            ? t('system.message.systemNotification')
            : item.subject || t('system.message.systemNotification');
        const instance = notification.info({
          title,
          content: () =>
            h(NotifyToastContent, {
              item,
              onClose: () => instance?.destroy(),
            }),
          duration: 5000,
          onClick: () => dispatchOpenMessageDrawer(),
        } as NotificationOptions);
      });

      if (items.length > 3) {
        notification.info({
          title: t('system.message.newMessageCount', { count: items.length }),
          duration: 4000,
          onClick: () => dispatchOpenMessageDrawer(),
        } as NotificationOptions);
      }
    },
    destroySystemNotify() {
      if (typeof this.notify?.destroy === 'function') {
        this.notify?.destroy();
      }
    },
    destroyPlatformNotify() {
      if (typeof this.platformNotify?.destroy === 'function') {
        this.platformNotify.destroy();
      }
      this.platformNotify = null;
    },
    showPlatformSystemAnnouncement(payload: { subject?: string; content?: string }) {
      this.destroyPlatformNotify();
      this.platformNotify = notification.create({
        title: '',
        content: () =>
          h(PlatformAnnouncementNotify, {
            subject: payload.subject || '',
            content: payload.content || '',
            onClose: () => this.destroyPlatformNotify(),
          }),
        duration: undefined,
        maxCount: 1,
      } as NotificationOptions);
    },
    showPlatformForceLogout(payload: { message?: string; countdownSeconds?: number }) {
      this.destroyPlatformNotify();
      const countdownSeconds = Math.max(5, Number(payload.countdownSeconds) || 30);
      this.platformNotify = notification.create({
        title: '',
        content: () =>
          h(PlatformForceLogoutNotify, {
            messageText: payload.message || '',
            countdownSeconds,
            onExpired: () => {
              this.destroyPlatformNotify();
              this.performSilentForceLogout();
            },
          }),
        duration: undefined,
        closable: false,
        maxCount: 1,
      } as NotificationOptions);
    },
    async performSilentForceLogout() {
      const appStore = useAppStoreAccessor();
      const tenantIdToRedirect = resolveTenantIdForAuthRedirect({
        routeTenantId: router.currentRoute.value.params?.tenantId,
        userTenantId: this.userInfo?.tenantId,
        appTenantId: appStore.tenantId,
      });
      const isDataSpecialist = this.userInfo.source === 'DATA_SPECIALIST';
      try {
        if (isDataSpecialist) {
          await dataSpecialistLogout();
        } else {
          await signout();
        }
      } catch {
        // 会话可能已被服务端清除
      }
      this.logoutCallBack();
      appStore.setTenantId(tenantIdToRedirect);
      appStore.setOrgId('');
      const targetName = isDataSpecialist ? 'dataSpecialistLogin' : 'login';
      if (targetName === 'login' && !tenantIdToRedirect) {
        await router.push({ name: 'platformLogin' });
        return;
      }
      await router.push({
        name: targetName,
        params: targetName === 'login' ? { tenantId: tenantIdToRedirect } : undefined,
      });
    },
    async initApiKeyList() {
      if (!hasAnyPermission(['PERSONAL_API_KEY:READ'])) return;
      try {
        const res = await getApiKeyList();
        this.apiKeyList = res.map((item) => ({
          ...item,
          isExpire: item.forever ? false : item.expireTime < Date.now(),
          desensitization: true,
          showDescInput: false,
        }));
      } catch (error) {
        // eslint-disable-next-line no-console
        console.log(error);
      }
    },
  },
});

export default useUserStore;
