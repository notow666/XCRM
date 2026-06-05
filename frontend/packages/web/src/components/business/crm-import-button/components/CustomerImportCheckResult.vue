<template>
  <CrmModal v-model:show="checkResultModal" size="small" :title="t('common.import')" @cancel="handleCancel">
    <div class="text-center">
      <CrmIcon :size="32" :type="resultStatus.icon" :class="resultStatus.color" />
      <div class="my-2 text-[16px] font-medium text-[var(--text-n1)]">
        {{ t(resultStatus.message) }}
      </div>
      <div class="leading-8 text-[var(--text-n4)]">
        <span>
          {{ t('crmImportButton.successfulCheck') }}
          <span class="mx-1 text-[var(--success-green)]">{{ validateResultInfo.successCount }}</span>
          {{ t('crmImportButton.countNumber') }};
        </span>
        <span v-if="validateResultInfo.failCount > 0">
          {{ t('crmImportButton.failCheck') }}
          <span class="mx-1 font-medium text-[var(--error-red)]">{{ validateResultInfo.failCount }}</span>
          {{ t('crmImportButton.countNumber') }};
        </span>
      </div>

      <div v-if="validateResultInfo.failCount > 0" class="mt-[12px] text-left">
        <div class="mb-[8px] text-[14px] font-medium text-[var(--text-n1)]">
          {{ t('crmImportButton.importErrorData') }}
        </div>
        <div class="flex items-center gap-[8px]">
          <span class="inline-block h-[10px] w-[10px] rounded-full bg-[#F5222D]"></span>
          <span class="text-[var(--text-n2)]">
            {{ t('crmImportButton.someUsersImportFailed') }}: {{ validateResultInfo.failCount }}
          </span>
        </div>
        <div class="mt-[12px]">
          <n-button
            v-if="validateResultInfo.errorFileId && props.downloadErrorApi"
            :loading="downloadLoading"
            quaternary
            type="primary"
            class="text-btn-primary !px-0"
            @click="downloadErrorFile"
          >
            {{ t('crmImportButton.downloadErrorFile') }}
          </n-button>
        </div>
      </div>

      <div v-if="!validateResultInfo.failCount" class="mt-[8px] text-[var(--text-n4)]">
        {{ t('crmImportButton.successImportActionTip') }}
      </div>
      <div v-else-if="validateResultInfo.successCount > 0" class="mt-[8px] text-[var(--text-n4)]">
        {{ t('crmImportButton.customerPartialCanImport') }}
      </div>
      <div v-else class="mt-[8px] text-[var(--text-n4)]">
        {{ t('crmImportButton.noImportableData') }}
      </div>
    </div>

    <template #footer>
      <div class="flex justify-end">
        <n-button v-if="validateResultInfo.failCount > 0" quaternary @click="handleBackUpload">
          {{ t('crmImportButton.backToUploadPage') }}
        </n-button>

        <n-button
          v-if="validateResultInfo.successCount > 0"
          :loading="props.importLoading"
          quaternary
          type="primary"
          class="text-btn-primary"
          @click="confirmImport"
        >
          {{
            validateResultInfo.failCount > 0
              ? t('crmImportButton.customerPartialContinueImport')
              : t('common.import')
          }}
        </n-button>
      </div>
    </template>
  </CrmModal>
</template>

<script setup lang="ts">
  import { ref } from 'vue';
  import { NButton, useMessage } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';
  import { downloadByteFile } from '@lib/shared/method';
  import { ValidateInfo } from '@lib/shared/models/system/org';

  import CrmIcon from '@/components/pure/crm-icon-font/index.vue';
  import CrmModal from '@/components/pure/crm-modal/index.vue';

  const { t } = useI18n();
  const Message = useMessage();

  const props = defineProps<{
    validateInfo: ValidateInfo;
    importLoading: boolean;
    downloadErrorApi?: (fileId: string) => Promise<any>;
  }>();

  const emit = defineEmits<{
    (e: 'save'): void;
  }>();

  const checkResultModal = defineModel<boolean>('show', {
    required: true,
    default: false,
  });

  const validateResultInfo = ref<ValidateInfo>(props.validateInfo);
  const downloadLoading = ref<boolean>(false);

  const resultStatus = computed(() => {
    const { successCount, failCount } = validateResultInfo.value;
    if (failCount > 0) {
      return {
        icon: 'iconicon_close_circle_filled',
        color: 'text-[var(--error-red)]',
        message: successCount > 0 ? 'crmImportButton.customerCheckNotPassed' : 'crmImportButton.failCheck',
      };
    }
    return {
      icon: 'iconicon_check_circle_filled',
      color: 'text-[var(--success-green)]',
      message: 'crmImportButton.successfulCheck',
    };
  });

  function handleCancel() {
    checkResultModal.value = false;
  }

  function handleBackUpload() {
    checkResultModal.value = false;
  }

  function confirmImport() {
    emit('save');
  }

  async function downloadErrorFile() {
    if (!validateResultInfo.value.errorFileId || !props.downloadErrorApi) return;
    try {
      downloadLoading.value = true;
      const res = await props.downloadErrorApi(validateResultInfo.value.errorFileId);
      downloadByteFile(res.data, validateResultInfo.value.errorFileName || 'error_file.xlsx');
    } catch {
      Message.error(t('common.downloadFailed'));
    } finally {
      downloadLoading.value = false;
    }
  }

  watch(
    () => props.validateInfo,
    (val) => {
      validateResultInfo.value = { ...val };
    },
    { deep: true }
  );
</script>
