<template>
  <n-button
    v-if="hasAnyPermission(['CUSTOMER_MANAGEMENT_POOL:IMPORT'])"
    type="primary"
    ghost
    class="n-btn-outline-primary"
    @click="handleImport"
  >
    {{ t('common.import') }}
  </n-button>

  <PoolImportModal
    v-model:show="importModal"
    :validating="validateLoading"
    @validate="validateTemplate"
    @download-template="downloadTemplate"
  />

  <PoolCheckResult
    v-model:show="checkResultModal"
    :check-response="checkResponse"
    :import-loading="importLoading"
    @save="importHandler"
    @download-error="downloadErrorFile"
    @back="importModal = true"
  />
</template>

<script setup lang="ts">
  import { ref } from 'vue';
  import { NButton, useMessage } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';
  import { downloadByteFile } from '@lib/shared/method';
  import type { PoolCustomerImportCheckResponse } from '@lib/shared/models/customer';

  import PoolCheckResult from './components/poolCheckResult.vue';
  import PoolImportModal from './components/poolImportModal.vue';

  import {
    downloadPoolCustomerTemplate,
    downloadPoolImportErrorFile,
    importPoolCustomer,
    preCheckImportPoolCustomer,
  } from '@/api/modules';
  import { hasAnyPermission } from '@/utils/permission';

  const { t } = useI18n();
  const Message = useMessage();

  const emit = defineEmits<{
    (e: 'importSuccess'): void;
  }>();

  const importModal = ref(false);
  const checkResultModal = ref(false);
  const validateLoading = ref(false);
  const importLoading = ref(false);
  const currentFile = ref<File | null>(null);
  const currentPoolId = ref('');

  const initCheckResponse: PoolCustomerImportCheckResponse = {
    passed: false,
    totalCount: 0,
    successCount: 0,
    errorCount: 0,
  };
  const checkResponse = ref<PoolCustomerImportCheckResponse>({ ...initCheckResponse });

  function handleImport() {
    importModal.value = true;
  }

  async function downloadTemplate() {
    try {
      const res = await downloadPoolCustomerTemplate();
      const fileName = res.headers?.['content-disposition']
        ? decodeURIComponent(
            res.headers['content-disposition'].match(/filename="?(.+?)"?$/)?.[1] ?? 'pool_import_template.xlsx'
          )
        : 'pool_import_template.xlsx';
      downloadByteFile(res.data, fileName);
    } catch (error) {
      Message.error(t('common.downloadFailed'));
    }
  }

  async function validateTemplate(file: File, poolId: string) {
    currentFile.value = file;
    currentPoolId.value = poolId;
    validateLoading.value = true;
    try {
      const res = await preCheckImportPoolCustomer(file, poolId);
      checkResponse.value = res.data;
      importModal.value = false;
      checkResultModal.value = true;
    } catch (error: any) {
      const errorMsg = error || t('poolImportButton.preCheckFailed');
      Message.error(errorMsg);
    } finally {
      validateLoading.value = false;
    }
  }

  async function importHandler() {
    if (!currentFile.value || !currentPoolId.value) return;
    try {
      importLoading.value = true;
      await importPoolCustomer(currentFile.value, currentPoolId.value);
      Message.success(t('common.importTaskCreate'));
      emit('importSuccess');
      checkResultModal.value = false;
      importModal.value = false;
    } catch (_error) {
      Message.error(t('poolImportButton.importFailed'));
    } finally {
      importLoading.value = false;
    }
  }

  async function downloadErrorFile() {
    if (!checkResponse.value.errorFileId) return;
    try {
      const res = await downloadPoolImportErrorFile(checkResponse.value.errorFileId);
      downloadByteFile(res.data, checkResponse.value.errorFileName || 'error_file.xlsx');
    } catch (error) {
      Message.error(t('common.downloadFailed'));
    }
  }
</script>

<style scoped></style>
