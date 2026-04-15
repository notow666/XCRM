<template>
  <CrmModal v-model:show="checkResultModal" size="small" :title="t('common.import')" @cancel="handleCancel">
    <div class="text-center">
      <CrmIcon
        :size="32"
        :type="checkResponse.passed ? 'iconicon_check_circle_filled' : 'iconicon_close_circle_filled'"
        :class="checkResponse.passed ? 'text-[var(--success-green)]' : 'text-[var(--error-red)]'"
      />
      <div class="my-2 text-[16px] font-medium text-[var(--text-n1)]">
        {{ checkResponse.passed ? t('poolImportButton.checkPassed') : t('poolImportButton.checkFailed') }}
      </div>
      <div class="leading-8 text-[var(--text-n4)]">
        <span>
          {{ t('crmImportButton.successfulCheck') }}
          <span class="mx-1 text-[var(--success-green)]">{{ checkResponse.successCount }}</span>
          {{ t('crmImportButton.countNumber') }};
        </span>
        <span v-if="checkResponse.errorCount > 0">
          {{ t('crmImportButton.failCheck') }}
          <span class="mx-1 font-medium text-[var(--error-red)]">{{ checkResponse.errorCount }}</span>
          {{ t('crmImportButton.countNumber') }};
        </span>
      </div>

      <div v-if="!checkResponse.passed && checkResponse.errorSummary" class="mt-[12px] text-left">
        <div class="mb-[8px] text-[14px] font-medium text-[var(--text-n1)]">
          {{ t('poolImportButton.errorSummary') }}
        </div>
        <div class="flex flex-col gap-[4px]">
          <div v-if="checkResponse.errorSummary.fieldValidationCount > 0" class="flex items-center gap-[8px]">
            <span class="inline-block h-[10px] w-[10px] rounded-full bg-[#F5222D]"></span>
            <span class="text-[var(--text-n2)]">
              {{ t('poolImportButton.fieldValidation') }}: {{ checkResponse.errorSummary.fieldValidationCount }}
            </span>
          </div>
          <div v-if="checkResponse.errorSummary.excelDuplicateCount > 0" class="flex items-center gap-[8px]">
            <span class="inline-block h-[10px] w-[10px] rounded-full bg-[#FADB14]"></span>
            <span class="text-[var(--text-n2)]">
              {{ t('poolImportButton.excelDuplicate') }}: {{ checkResponse.errorSummary.excelDuplicateCount }}
            </span>
          </div>
          <div v-if="checkResponse.errorSummary.privateConflictCount > 0" class="flex items-center gap-[8px]">
            <span class="inline-block h-[10px] w-[10px] rounded-full bg-[#FA8C16]"></span>
            <span class="text-[var(--text-n2)]">
              {{ t('poolImportButton.privateConflict') }}: {{ checkResponse.errorSummary.privateConflictCount }}
            </span>
          </div>
          <div v-if="checkResponse.errorSummary.otherPoolConflictCount > 0" class="flex items-center gap-[8px]">
            <span class="inline-block h-[10px] w-[10px] rounded-full bg-[#1890FF]"></span>
            <span class="text-[var(--text-n2)]">
              {{ t('poolImportButton.otherPoolConflict') }}: {{ checkResponse.errorSummary.otherPoolConflictCount }}
            </span>
          </div>
        </div>
        <div class="mt-[12px]">
          <n-button quaternary type="primary" @click="handleDownloadError">
            {{ t('poolImportButton.downloadErrorFile') }}
          </n-button>
        </div>
      </div>

      <div v-if="checkResponse.passed" class="mt-[8px] text-[var(--text-n4)]">
        {{ t('poolImportButton.canImport') }}
      </div>
      <div v-if="!checkResponse.passed" class="mt-[8px] text-[var(--text-n4)]">
        {{ t('poolImportButton.modifyAndReUpload') }}
      </div>
    </div>

    <template #footer>
      <div class="flex justify-end">
        <n-button v-if="!checkResponse.passed" quaternary @click="handleBack">
          {{ t('poolImportButton.backToUpload') }}
        </n-button>

        <n-button
          v-if="checkResponse.passed"
          :loading="importLoading"
          quaternary
          type="primary"
          class="text-btn-primary"
          @click="handleImport"
        >
          {{ t('common.import') }}
        </n-button>
      </div>
    </template>
  </CrmModal>
</template>

<script setup lang="ts">
  import { NButton } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';
  import type { PoolCustomerImportCheckResponse } from '@lib/shared/models/customer';

  import CrmIcon from '@/components/pure/crm-icon-font/index.vue';
  import CrmModal from '@/components/pure/crm-modal/index.vue';

  const { t } = useI18n();

  const props = defineProps<{
    checkResponse: PoolCustomerImportCheckResponse;
    importLoading: boolean;
  }>();

  const emit = defineEmits<{
    (e: 'save'): void;
    (e: 'downloadError'): void;
    (e: 'back'): void;
  }>();

  const checkResultModal = defineModel<boolean>('show', {
    required: true,
    default: false,
  });

  function handleCancel() {
    checkResultModal.value = false;
  }

  function handleImport() {
    emit('save');
  }

  function handleDownloadError() {
    emit('downloadError');
  }

  function handleBack() {
    checkResultModal.value = false;
    emit('back');
  }
</script>

<style scoped></style>
