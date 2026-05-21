<template>
  <CrmDrawer
    v-model:show="showDrawer"
    :width="520"
    :title="t('module.customerAutoDelete')"
    :footer="true"
    :auto-focus="false"
    no-padding
    :confirm-loading="saveLoading"
    @confirm="handleSave"
  >
    <template #footerLeft>
      <NButton v-if="configExists" type="error" ghost @click="handleDelete">
        {{ t('common.delete') }}
      </NButton>
    </template>
    <div class="p-[24px]">
      <div class="mb-[8px] text-[var(--text-n2)]">{{ t('module.customerAutoDeleteDays') }}</div>
      <n-input-number
        v-model:value="days"
        :min="1"
        :max="365"
        :placeholder="t('module.customerAutoDeleteDaysPlaceholder')"
        style="width: 220px"
      />
      <div class="mt-[12px]" style="font-size: 12px; line-height: 20px; color: #e88080 !important">
        {{ t('module.customerAutoDeleteTip') }}
      </div>
    </div>
  </CrmDrawer>
</template>

<script setup lang="ts">
  /* eslint-disable simple-import-sort/imports */
  import { ref, watch } from 'vue';
  import { NButton, NInputNumber, useMessage } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';

  import { deleteCustomerAutoDelete, getCustomerAutoDelete, saveCustomerAutoDelete } from '@/api/modules';
  import CrmDrawer from '@/components/pure/crm-drawer/index.vue';

  const { t } = useI18n();
  const Message = useMessage();

  const showDrawer = defineModel<boolean>('visible', {
    required: true,
  });

  const days = ref<number>(30);
  const configExists = ref(false);
  const saveLoading = ref(false);

  async function loadConfig() {
    try {
      const config = await getCustomerAutoDelete();
      if (config) {
        configExists.value = true;
        days.value = config.days || 30;
      } else {
        configExists.value = false;
        days.value = 30;
      }
    } catch (error) {
      // eslint-disable-next-line no-console
      console.error('加载客户定时删除配置失败', error);
    }
  }

  async function handleSave() {
    if (!days.value || days.value < 1) {
      Message.warning(t('module.customerAutoDeleteDaysRequired'));
      return;
    }
    saveLoading.value = true;
    try {
      await saveCustomerAutoDelete({
        days: days.value,
      });
      Message.success(t('common.saveSuccess'));
      configExists.value = true;
      showDrawer.value = false;
    } catch (error) {
      // eslint-disable-next-line no-console
      console.error('保存客户定时删除配置失败', error);
    } finally {
      saveLoading.value = false;
    }
  }

  async function handleDelete() {
    try {
      await deleteCustomerAutoDelete();
      Message.success(t('common.deleteSuccess'));
      configExists.value = false;
      days.value = 30;
    } catch (error) {
      // eslint-disable-next-line no-console
      console.error('删除客户定时删除配置失败', error);
    }
  }

  watch(
    () => showDrawer.value,
    (val) => {
      if (val) {
        loadConfig();
      }
    },
    { immediate: true }
  );
</script>
