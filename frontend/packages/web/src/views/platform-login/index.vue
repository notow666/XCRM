<template>
  <n-scrollbar style="width: 100vw; height: 100vh">
    <div class="platform-login-page" style="min-width: 1200px">
      <banner/>
      <div class="platform-login-form h-[100vh] flex-1">
        <div class="login-form" style="height: 100vh;">
          <div class="title">
<!--            <div class="flex justify-center">-->
<!--              <img :src="loginLogo" class="h-[52px] w-[240px]"/>-->
<!--            </div>-->
            <div class="title-0 mt-[16px] flex justify-center">
              <span class="title-welcome">管理中心登录</span>
            </div>
          </div>

          <div class="form mt-[40px] min-w-[480px]">
            <div class="mb-7 text-[18px] font-medium text-[var(--primary-8)]">账号登录</div>
            <n-form :model="form">
              <n-form-item class="login-form-item">
                <n-input
                  v-model:value="form.username"
                  type="text"
                  placeholder="请输入用户名"
                  maxlength="64"
                />
              </n-form-item>
              <n-form-item class="login-form-item">
                <n-input
                  v-model:value="form.password"
                  type="password"
                  placeholder="密码"
                  @keydown.enter="handleLogin"
                />
              </n-form-item>
              <div class="mt-[12px] mb-7">
                <n-button type="primary" size="large" block :loading="loading" @click="handleLogin">登录</n-button>
              </div>
            </n-form>
          </div>
        </div>
      </div>
    </div>
  </n-scrollbar>
</template>

<script setup lang="ts">
import {computed, reactive, ref} from 'vue';
import {useRouter} from 'vue-router';
import {NButton, NForm, NFormItem, NInput, NScrollbar, useMessage} from 'naive-ui';

import {setLoginExpires, setToken} from '@lib/shared/method/auth';

import banner from '@/views/base/login/components/banner.vue';

import {platformLogin} from '@/api/modules';
import {defaultLoginLogo} from '@/config/business';
import useAppStore from '@/store/modules/app';
import useUserStore from '@/store/modules/user';

const appStore = useAppStore();
const router = useRouter();
const message = useMessage();
const userStore = useUserStore();
const loading = ref(false);
const loginLogo = computed(() => appStore.pageConfig.loginLogo[0]?.url ?? defaultLoginLogo);

const form = reactive({
  username: '',
  password: '',
});

async function handleLogin() {
  if (!form.username || !form.password) {
    message.error('请输入用户名和密码');
    return;
  }
  loading.value = true;
  try {
    const res = await platformLogin({
      username: form.username,
      password: form.password,
    });
    setToken(res.sessionId, res.csrfToken);
    setLoginExpires();
    userStore.setInfo(res as any);
    appStore.setTenantId('');
    appStore.setOrgId('');
    await router.replace({name: 'managementCenterOverview'});
    message.success('登录成功');
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped lang="less">
.platform-login-page {
  display: flex;
  align-items: center;
}

.platform-login-form {
  width: 40%;
  min-width: 540px;
  padding: 0 40px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.title {
  .title-welcome {
    font-size: 28px;
    font-weight: 600;
    color: var(--text-n1);
  }
}

.login-form {
  display: flex;
  flex: 1 1 0%;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  transform: translateY(-10%);
}

.login-form .form {
  position: relative;
  padding: 40px;
  border-radius: var(--border-radius-large);
  background-color: var(--text-n10);
  box-shadow: 0 8px 10px 0 #3232330d, 0 16px 24px 0 #3232330d, 0 6px 30px 0 #3232330d;
}

.login-form .form .login-form-item {
  display: block;
}

.subtitle {
  color: var(--text-n4);
}
</style>
