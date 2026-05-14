<template>
  <div class="ds-import p-6">
    <div class="mb-6 flex items-center justify-between">
      <div class="text-[18px] font-semibold">公海客户导入</div>
      <n-button quaternary type="primary" @click="handleLogout">退出登录</n-button>
    </div>

    <n-card class="max-w-[960px]">
      <div class="mb-4">
        <div class="mb-2 text-[14px] text-[var(--text-n2)]">导入租户</div>
        <n-select
          v-model:value="selectedTenantId"
          :options="tenantOptions"
          value-field="tenantId"
          label-field="name"
          placeholder="请选择租户"
          class="w-full max-w-[480px]"
          @update:value="onTenantChange"
        />
        <div class="mt-1 max-w-[480px] text-xs leading-relaxed text-[var(--text-n4)]">
          {{ t('dataSpecialistImport.tenantSwitchHint') }}
        </div>
      </div>

      <div class="mb-4">
        <div class="mb-2 text-[14px] text-[var(--text-n2)]">导入公海池</div>
        <n-select
          v-model:value="selectedPoolId"
          :options="poolOptions"
          value-field="id"
          label-field="name"
          placeholder="请选择公海池"
          :disabled="!selectedTenantId || poolLoading"
          class="w-full max-w-[480px]"
        />
      </div>

      <n-alert type="default" class="mb-4">
        <div class="flex flex-wrap items-center gap-2">
          <span>{{ t('poolImportButton.importAlertDesc') }}</span>
          <n-button text type="primary" :disabled="!selectedTenantId" @click="handleDownloadTemplate">
            {{ t('crmImportButton.downloadTemplate') }}
          </n-button>
        </div>
      </n-alert>

      <CrmUpload
        v-model:file-list="fileList"
        :is-all-screen="true"
        accept="excel"
        :max-size="10"
        size-unit="MB"
        directory-dnd
        :file-type-tip="t('crmImportButton.onlyAllowFileTypeTip')"
      />

      <div class="mt-4 flex gap-2">
        <n-button type="primary" :disabled="!canPreCheck" :loading="validateLoading" @click="runPreCheck">
          {{ t('crmImportButton.validateTemplate') }}
        </n-button>
      </div>
    </n-card>

    <PoolCheckResult
      v-model:show="checkResultModal"
      :check-response="checkResponse"
      :import-loading="importLoading"
      @save="runImport"
      @download-error="downloadErrorFile"
      @back="checkResultModal = false"
    />
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
  import { NAlert, NButton, NCard, NSelect, useMessage } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';
  import { downloadByteFile, getGenerateId } from '@lib/shared/method';
  import type { PoolCustomerImportCheckResponse } from '@lib/shared/models/customer';

  import CrmUpload from '@/components/pure/crm-upload/index.vue';
  import type { CrmFileItem } from '@/components/pure/crm-upload/types';
  import PoolCheckResult from '@/components/business/crm-pool-import-button/components/poolCheckResult.vue';

  import {
    dataSpecialistDownloadPoolErrorFile,
    dataSpecialistDownloadPoolTemplate,
    dataSpecialistImportPool,
    dataSpecialistListPools,
    dataSpecialistListTenants,
    dataSpecialistPreCheckPoolImport,
  } from '@/api/modules';
  import useUser from '@/hooks/useUser';
  import useAppStore from '@/store/modules/app';
  import useUserStore from '@/store/modules/user';

  const { t } = useI18n();
  const Message = useMessage();
  const { logout } = useUser();
  const userStore = useUserStore();
  const appStore = useAppStore();

  const tenantOptions = ref<Array<{ tenantId: string; name: string; code: string }>>([]);
  const poolOptions = ref<Array<{ id: string; name: string }>>([]);
  const selectedTenantId = ref<string | null>(null);
  const selectedPoolId = ref<string | null>(null);
  const poolLoading = ref(false);
  const fileList = ref<CrmFileItem[]>([]);
  const validateLoading = ref(false);
  const importLoading = ref(false);
  const checkResultModal = ref(false);

  const initCheckResponse: PoolCustomerImportCheckResponse = {
    passed: false,
    totalCount: 0,
    successCount: 0,
    errorCount: 0,
  };
  const checkResponse = ref<PoolCustomerImportCheckResponse>({ ...initCheckResponse });

  const canPreCheck = computed(() => !!selectedTenantId.value && !!selectedPoolId.value && fileList.value.length > 0);

  let currentFile: File | null = null;

  async function loadTenants() {
    try {
      const list = await dataSpecialistListTenants();
      tenantOptions.value = Array.isArray(list) ? list : [];
    } catch {
      Message.error(t('common.loadFailed'));
    }
  }

  async function loadPools(tenantId: string) {
    poolLoading.value = true;
    try {
      const list = await dataSpecialistListPools(tenantId);
      poolOptions.value = (list ?? []).map((p: any) => ({ id: p.id, name: p.name }));
    } catch {
      Message.error(t('common.loadFailed'));
      poolOptions.value = [];
    } finally {
      poolLoading.value = false;
    }
  }

  function onTenantChange(tenantId: string | null) {
    selectedPoolId.value = null;
    poolOptions.value = [];
    if (tenantId) {
      loadPools(tenantId);
    }
  }

  function resetWorkbenchAfterTenantChange(oldTid: string | null | undefined, newTid: string | null) {
    if (oldTid != null && oldTid !== newTid) {
      fileList.value = [];
      currentFile = null;
      checkResponse.value = { ...initCheckResponse };
      checkResultModal.value = false;
      importLoading.value = false;
      validateLoading.value = false;
    }
  }

  async function handleDownloadTemplate() {
    if (!selectedTenantId.value) return;
    try {
      const res = await dataSpecialistDownloadPoolTemplate(selectedTenantId.value);
      const fileName = res.headers?.['content-disposition']
        ? decodeURIComponent(
            res.headers['content-disposition'].match(/filename="?(.+?)"?$/)?.[1] ?? 'pool_import_template.xlsx'
          )
        : 'pool_import_template.xlsx';
      downloadByteFile(res.data, fileName);
    } catch {
      Message.error(t('common.downloadFailed'));
    }
  }

  async function runPreCheck() {
    if (!selectedTenantId.value || !selectedPoolId.value || !fileList.value.length) return;
    const file = fileList.value[0].file as File;
    currentFile = file;
    validateLoading.value = true;
    try {
      const res = await dataSpecialistPreCheckPoolImport(selectedTenantId.value, file, selectedPoolId.value);
      const payload = (res as any)?.data ?? res;
      checkResponse.value = payload as PoolCustomerImportCheckResponse;
      checkResultModal.value = true;
    } catch (error: any) {
      Message.error(error || t('poolImportButton.preCheckFailed'));
    } finally {
      validateLoading.value = false;
    }
  }

  async function runImport() {
    if (!selectedTenantId.value || !selectedPoolId.value || !currentFile) return;
    importLoading.value = true;
    try {
      await dataSpecialistImportPool(selectedTenantId.value, currentFile, selectedPoolId.value);
      Message.success(t('common.importTaskCreate'));
      checkResultModal.value = false;
      fileList.value = [];
    } catch {
      Message.error(t('poolImportButton.importFailed'));
    } finally {
      importLoading.value = false;
    }
  }

  async function downloadErrorFile() {
    if (!selectedTenantId.value || !checkResponse.value.errorFileId) return;
    try {
      const res = await dataSpecialistDownloadPoolErrorFile(selectedTenantId.value, checkResponse.value.errorFileId);
      downloadByteFile(res.data, checkResponse.value.errorFileName || 'error_file.xlsx');
    } catch {
      Message.error(t('common.downloadFailed'));
    }
  }

  watch(selectedTenantId, (tid, oldTid) => {
    resetWorkbenchAfterTenantChange(oldTid, tid);
    if (!tid) {
      poolOptions.value = [];
      selectedPoolId.value = null;
    }
  });

  onMounted(async () => {
    if (!userStore.clientIdRandomId) {
      userStore.$patch({ clientIdRandomId: getGenerateId() });
    }
    await appStore.connectSystemMessageSSE(() => {});
    loadTenants();
  });

  onUnmounted(async () => {
    await appStore.disconnectSystemMessageSSE();
  });

  async function handleLogout() {
    await logout(undefined, undefined, false);
  }
</script>

<style scoped>
  .ds-import {
    min-height: 100vh;
    background: var(--fill-1);
  }
</style>
