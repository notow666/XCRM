<template>
  <div ref="customerCardRef" class="customer-page h-full">
    <CrmCard no-content-padding hide-footer class="h-full">
      <div class="customer-page__body h-full min-h-0 px-[16px] pt-[16px]">
        <CustomerTable :form-key="FormDesignKeyEnum.CUSTOMER" />
      </div>
    </CrmCard>
  </div>
</template>

<script setup lang="ts">
  import { FormDesignKeyEnum } from '@lib/shared/enums/formDesignEnum';

  import CrmCard from '@/components/pure/crm-card/index.vue';
  import CustomerTable from './components/customerTable.vue';
</script>

<style lang="less" scoped>
  :deep(.n-tabs-scroll-padding) {
    width: 16px !important;
  }
  .customer-page {
    :deep(.n-card) {
      display: flex;
      flex-direction: column;
      min-height: 0;
    }
    :deep(.n-card__content) {
      flex: 1;
      min-height: 0;
      display: flex;
      flex-direction: column;
    }
    :deep(.n-scrollbar) {
      flex: 1;
      min-height: 0;
    }
    :deep(.n-scrollbar-container) {
      height: 100% !important;
      max-height: 100% !important;
      overflow: hidden !important;
      display: flex;
      flex-direction: column;
    }
    :deep(.n-scrollbar-content) {
      height: 100%;
      display: flex;
      flex-direction: column;
      min-height: 0;
    }
    .customer-page__body {
      display: flex;
      flex-direction: column;
      flex: 1;
      min-height: 0;
    }
  }
</style>
