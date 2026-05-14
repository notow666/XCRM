<template>
  <n-scrollbar style="width: 100vw; height: 100vh">
    <div class="ds-login-page flex min-h-[100vh] items-center justify-center bg-[var(--fill-1)]">
      <div class="w-[420px] rounded-lg bg-white p-8 shadow-sm">
        <div class="mb-6 text-center text-[20px] font-semibold text-[var(--text-n1)]">数据专员登录</div>
        <n-form :model="form">
          <n-form-item>
            <n-input v-model:value="form.username" type="text" placeholder="用户名" maxlength="64" />
          </n-form-item>
          <n-form-item>
            <n-input
              v-model:value="form.password"
              type="password"
              placeholder="密码"
              @keydown.enter="handleLogin"
            />
          </n-form-item>
          <n-button type="primary" size="large" block :loading="loading" @click="handleLogin">登录</n-button>
        </n-form>
      </div>
    </div>
  </n-scrollbar>
</template>

<script setup lang="ts">
  import { reactive, ref } from 'vue';
  import { useRouter } from 'vue-router';
  import { NButton, NForm, NFormItem, NInput, NScrollbar, useMessage } from 'naive-ui';

  import { getGenerateId } from '@lib/shared/method';
  import { setLoginExpires, setToken } from '@lib/shared/method/auth';

  import { dataSpecialistLogin } from '@/api/modules';
  import useAppStore from '@/store/modules/app';
  import useUserStore from '@/store/modules/user';

  const appStore = useAppStore();
  const router = useRouter();
  const message = useMessage();
  const userStore = useUserStore();
  const loading = ref(false);

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
      const res = await dataSpecialistLogin({
        username: form.username,
        password: form.password,
      });
      setToken(res.sessionId, res.csrfToken);
      setLoginExpires();
      userStore.setInfo(res as any);
      userStore.$patch({ clientIdRandomId: getGenerateId() });
      appStore.setTenantId('');
      appStore.setOrgId('');
      await router.replace({ name: 'dataSpecialistImport' });
      message.success('登录成功');
    } finally {
      loading.value = false;
    }
  }
</script>

<style scoped>
  .ds-login-page {
    min-width: 360px;
  }
</style>
