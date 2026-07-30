<template>
  <CrmDrawer
    v-model:show="showDrawer"
    :width="560"
    :title="t('module.customerContractDeletePolicy')"
    :footer="true"
    :auto-focus="false"
    no-padding
    :confirm-loading="saveLoading"
    @confirm="handleSave"
  >
    <div class="p-[24px]">
      <n-radio-group v-model:value="policy" class="flex flex-col gap-[16px]">
        <n-radio value="CASCADE">
          <div>
            <div class="text-[var(--text-n1)]">{{ t('module.customerContractDeleteCascade') }}</div>
            <div class="mt-[4px] text-[12px] text-[var(--text-n4)]">
              {{ t('module.customerContractDeleteCascadeTip') }}
            </div>
          </div>
        </n-radio>
        <n-radio value="KEEP_CONTRACT">
          <div>
            <div class="text-[var(--text-n1)]">{{ t('module.customerContractDeleteKeep') }}</div>
            <div class="mt-[4px] text-[12px] text-[var(--text-n4)]">
              {{ t('module.customerContractDeleteKeepTip') }}
            </div>
          </div>
        </n-radio>
      </n-radio-group>
      <div class="mt-[20px] text-[12px] leading-[20px] text-[var(--text-n4)]">
        {{ t('module.customerContractDeletePolicyPendingTip') }}
      </div>
    </div>
  </CrmDrawer>
</template>

<script setup lang="ts">
  import { NRadio, NRadioGroup, useMessage } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';
  import type { CustomerContractDeletePolicy } from '@lib/shared/models/system/module';

  import CrmDrawer from '@/components/pure/crm-drawer/index.vue';

  import { editCustomerContractDeletePolicy, getCustomerContractDeletePolicy } from '@/api/modules';

  const { t } = useI18n();
  const Message = useMessage();

  const showDrawer = defineModel<boolean>('visible', {
    required: true,
  });
  const policy = ref<CustomerContractDeletePolicy>('CASCADE');
  const saveLoading = ref(false);

  async function loadConfig() {
    try {
      const config = await getCustomerContractDeletePolicy();
      policy.value = config?.policy || 'CASCADE';
    } catch (error) {
      // eslint-disable-next-line no-console
      console.error('加载客户合同删除策略失败', error);
    }
  }

  async function handleSave() {
    saveLoading.value = true;
    try {
      await editCustomerContractDeletePolicy(policy.value);
      Message.success(t('common.saveSuccess'));
      showDrawer.value = false;
    } catch (error) {
      // eslint-disable-next-line no-console
      console.error('保存客户合同删除策略失败', error);
    } finally {
      saveLoading.value = false;
    }
  }

  watch(
    () => showDrawer.value,
    (visible) => {
      if (visible) {
        loadConfig();
      }
    }
  );
</script>
