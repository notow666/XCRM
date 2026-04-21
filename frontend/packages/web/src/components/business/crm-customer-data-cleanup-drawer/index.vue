<template>
  <CrmDrawer
    v-model:show="showDrawer"
    :width="700"
    :title="t('module.customerDataCleanup')"
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
      <div class="mb-[16px]">
        <div class="mb-[8px] text-[var(--text-n2)]">{{ t('module.dataCleanupDays') }}</div>
        <n-input-number
          v-model:value="days"
          :min="1"
          :max="365"
          :placeholder="t('module.dataCleanupDaysPlaceholder')"
          style="width: 200px"
        />
      </div>
      <div class="mb-[8px] text-[var(--text-n2)]">{{ t('module.dataCleanupFields') }}</div>
      <n-scrollbar style="max-height: 400px">
        <n-checkbox-group v-model:value="selectedFields">
          <div class="flex flex-wrap gap-[12px]">
            <n-checkbox
              v-for="field in fieldList"
              :key="field.id"
              :value="field.id"
              class="min-w-[120px]"
            >
              {{ field.name }}
            </n-checkbox>
          </div>
        </n-checkbox-group>
      </n-scrollbar>
    </div>
  </CrmDrawer>
</template>

<script setup lang="ts">
  /* eslint-disable simple-import-sort/imports */
  import { ref, watch } from 'vue';
  import { NInputNumber, NCheckboxGroup, NCheckbox, NScrollbar, useMessage, NButton } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';

  import { getCustomerDataCleanup, getCustomerFormConfig, saveCustomerDataCleanup, deleteCustomerDataCleanup } from '@/api/modules';
  import CrmDrawer from '@/components/pure/crm-drawer/index.vue';

  const EXCLUDED_FIELDS = ['name', 'mobile', 'owner'];
  const EXCLUDED_TYPES = ['DIVIDER'];

  const { t } = useI18n();
  const Message = useMessage();

  const showDrawer = defineModel<boolean>('visible', {
    required: true,
  });

  const days = ref<number>(30);
  const selectedFields = ref<string[]>([]);
  const configExists = ref(false);
  const saveLoading = ref(false);
const fieldList = ref<{ id: string; name: string }[]>([]);

async function loadConfig() {
    try {
      const res = await getCustomerFormConfig();
      const fields: { id: string; name: string }[] = [];

      if (res.fields) {
        res.fields.forEach((field: any) => {
          if (field.id && field.name && !EXCLUDED_FIELDS.includes(field.businessKey) && !EXCLUDED_TYPES.includes(field.type)) {
            fields.push({ id: field.id, name: field.name });
          }
        });
      }

      fieldList.value = fields;

      const config = await getCustomerDataCleanup();
      if (config) {
        configExists.value = true;
        days.value = config.days || 30;
        selectedFields.value = config.fieldIds || [];
      } else {
        configExists.value = false;
      }
    } catch (error) {
      // eslint-disable-next-line no-console
      console.error('加载配置失败', error);
    }
  }

  async function handleSave() {
    if (!days.value || days.value < 1) {
      Message.warning(t('module.dataCleanupDaysRequired'));
      return;
    }
    if (selectedFields.value.length === 0) {
      Message.warning(t('module.dataCleanupFieldsRequired'));
      return;
    }
    saveLoading.value = true;
    try {
      await saveCustomerDataCleanup({
        fieldIds: selectedFields.value,
        days: days.value,
      });
      Message.success(t('common.saveSuccess'));
      configExists.value = true;
      showDrawer.value = false;
    } catch (error) {
      // eslint-disable-next-line no-console
      console.error('保存配置失败', error);
    } finally {
      saveLoading.value = false;
    }
  }

  async function handleDelete() {
    try {
      await deleteCustomerDataCleanup();
      Message.success(t('common.deleteSuccess'));
      configExists.value = false;
      selectedFields.value = [];
      days.value = 30;
    } catch (error) {
      // eslint-disable-next-line no-console
      console.error('删除配置失败', error);
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