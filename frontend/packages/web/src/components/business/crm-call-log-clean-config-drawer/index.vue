<template>
  <CrmDrawer
    v-model:show="showDrawer"
    :width="560"
    :title="t('module.callLogCleanConfig')"
    :footer="true"
    :auto-focus="false"
    :confirm-loading="saveLoading"
    @confirm="handleSave"
  >
    <div class="flex items-center justify-between">
      <span class="text-[var(--text-n2)]">{{ t('module.callLogCleanEnable') }}</span>
      <n-switch v-model:value="form.enable" />
    </div>
    <div class="mb-[8px] mt-[20px] flex items-center justify-between text-[var(--text-n2)]">
      <span>{{ t('module.callLogCleanEmployees') }}</span>
      <n-button v-if="selectedUsers.length" text type="primary" size="tiny" @click="handleClearUsers">
        {{ t('common.clear') }}
      </n-button>
    </div>
    <CrmUserTagSelector
      v-model:value="form.userIds"
      v-model:selected-list="selectedUsers"
      :api-type-key="MemberApiTypeEnum.SYSTEM_ORG_USER"
      :drawer-title="t('module.callLogCleanEmployees')"
      :expand-org-to-members="true"
    />
    <div class="mt-[12px] text-[12px] leading-[20px] text-[var(--error-color)]">
      {{ t('module.callLogCleanTip') }}
    </div>
  </CrmDrawer>
</template>

<script setup lang="ts">
  /* eslint-disable simple-import-sort/imports */
  import { NButton, NSwitch, useMessage } from 'naive-ui';

  import { MemberApiTypeEnum } from '@lib/shared/enums/moduleEnum';
  import { useI18n } from '@lib/shared/hooks/useI18n';
  import type { SelectedUsersItem } from '@lib/shared/models/system/module';

  import { getCallLogCleanConfig, getUserOptions, saveCallLogCleanConfig } from '@/api/modules';
  import CrmUserTagSelector from '@/components/business/crm-user-tag-selector/index.vue';
  import CrmDrawer from '@/components/pure/crm-drawer/index.vue';

  const { t } = useI18n();
  const Message = useMessage();
  const showDrawer = defineModel<boolean>('visible', { required: true });
  const saveLoading = ref(false);
  const form = ref({ enable: false, userIds: [] as string[] });
  const selectedUsers = ref<SelectedUsersItem[]>([]);

  function handleClearUsers() {
    form.value.userIds = [];
    selectedUsers.value = [];
  }

  async function loadConfig() {
    try {
      const [config, userOptions] = await Promise.all([getCallLogCleanConfig(), getUserOptions()]);
      form.value = { enable: Boolean(config?.enable), userIds: config?.userIds ?? [] };
      const optionMap = new Map<string, string>(
        (userOptions ?? []).map((item: any) => [String(item.id ?? item.value), String(item.name ?? item.label ?? '')])
      );
      selectedUsers.value = form.value.userIds.map((id) => ({ id, name: optionMap.get(id) || id }));
    } catch (error) {
      // 接口层会统一提示错误，这里保留上下文日志便于前端排查。
      // eslint-disable-next-line no-console
      console.error('加载定时清除通话记录配置失败', error);
    }
  }

  async function handleSave() {
    if (form.value.enable && form.value.userIds.length === 0) {
      Message.warning(t('module.callLogCleanEmployeesRequired'));
      return;
    }
    saveLoading.value = true;
    try {
      await saveCallLogCleanConfig(form.value);
      Message.success(t('common.saveSuccess'));
      showDrawer.value = false;
    } catch (error) {
      // eslint-disable-next-line no-console
      console.error('保存定时清除通话记录配置失败', error);
    } finally {
      saveLoading.value = false;
    }
  }

  watch(
    () => showDrawer.value,
    (visible) => {
      if (visible) loadConfig();
    }
  );
</script>
