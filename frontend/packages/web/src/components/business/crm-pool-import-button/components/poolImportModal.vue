<template>
  <CrmModal
    v-model:show="showModal"
    size="medium"
    :title="t('poolImportButton.title')"
    :ok-loading="props.validating"
    :positive-text="t('crmImportButton.validateTemplate')"
    :ok-button-props="{ disabled: props.validating || !selectedPoolId || fileList.length < 1 }"
    @cancel="cancel"
    @confirm="validate"
  >
    <div>
      <n-alert type="default" class="mb-[16px]">
        <template #icon>
          <CrmIcon type="iconicon_info_circle_filled" :size="20" />
        </template>
        <div class="flex items-center gap-[16px]">
          {{ t('poolImportButton.importAlertDesc') }}
          <div class="flex cursor-pointer items-center gap-[8px]" @click="handleDownloadTemplate">
            <CrmIcon type="iconicon_file-excel_colorful" :size="16" />
            <div class="text-[var(--primary-8)]">{{ t('crmImportButton.downloadTemplate') }}</div>
          </div>
        </div>
      </n-alert>

      <div class="mb-[16px]">
        <div class="mb-[8px] text-[14px] text-[var(--text-n1)]">{{ t('poolImportButton.selectPool') }}</div>
        <n-select
          v-model:value="selectedPoolId"
          :options="poolOptions"
          value-field="id"
          label-field="name"
          :placeholder="t('poolImportButton.selectPoolPlaceholder')"
          :disabled="props.validating"
          class="w-full"
        />
      </div>

      <CrmUpload
        v-model:file-list="fileList"
        :is-all-screen="true"
        accept="excel"
        :max-size="100"
        size-unit="MB"
        directory-dnd
        :file-type-tip="t('crmImportButton.onlyAllowFileTypeTip')"
        :disabled="props.validating"
      />
    </div>
  </CrmModal>
</template>

<script setup lang="ts">
  import { ref, watch } from 'vue';
  import { NAlert, NSelect, useMessage } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';
  import useLocale from '@lib/shared/locale/useLocale';
  import { downloadByteFile } from '@lib/shared/method';

  import CrmIcon from '@/components/pure/crm-icon-font/index.vue';
  import CrmModal from '@/components/pure/crm-modal/index.vue';
  import CrmUpload from '@/components/pure/crm-upload/index.vue';
  import type { CrmFileItem } from '@/components/pure/crm-upload/types';

  import { downloadPoolCustomerTemplate, getOpenSeaOptions } from '@/api/modules';

  const { loading } = useMessage();
  const { currentLocale } = useLocale(loading);
  const { t } = useI18n();
  const Message = useMessage();

  const emit = defineEmits<{
    (e: 'validate', file: File, poolId: string): void;
  }>();

  const props = withDefaults(
    defineProps<{
      validating?: boolean;
    }>(),
    {
      validating: false,
    }
  );

  const showModal = defineModel<boolean>('show', {
    required: true,
    default: false,
  });

  const selectedPoolId = ref('');
  const poolOptions = ref<Array<{ id: string; name: string }>>([]);
  const fileList = ref<CrmFileItem[]>([]);

  async function loadPoolOptions() {
    try {
      const res = await getOpenSeaOptions();
      poolOptions.value = res ?? [];
    } catch (_error) {
      Message.error(t('common.loadFailed'));
    }
  }

  watch(showModal, (val) => {
    if (val) {
      loadPoolOptions();
    }
  });

  function cancel() {
    showModal.value = false;
    fileList.value = [];
    selectedPoolId.value = '';
  }

  async function handleDownloadTemplate() {
    try {
      const res = await downloadPoolCustomerTemplate();
      const fileName = res.headers?.['content-disposition']
        ? decodeURIComponent(
            res.headers['content-disposition'].match(/filename="?(.+?)"?$/)?.[1] ?? 'pool_import_template.xlsx'
          )
        : 'pool_import_template.xlsx';
      downloadByteFile(res.data, fileName);
    } catch (_error) {
      Message.error(t('common.downloadFailed'));
    }
  }

  async function validate() {
    if (!selectedPoolId.value) {
      Message.warning(t('poolImportButton.selectPoolPlaceholder'));
      return;
    }
    if (fileList.value.length < 1) {
      Message.warning(t('crmImportButton.onlyAllowFileTypeTip'));
      return;
    }
    const file = fileList.value[0].file as File;
    try {
      await file.arrayBuffer();
    } catch {
      Message.warning(t('crmImportButton.fileChange'));
      return;
    }
    emit('validate', file, selectedPoolId.value);
  }
</script>

<style scoped></style>
