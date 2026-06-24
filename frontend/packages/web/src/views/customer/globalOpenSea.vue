<template>
  <div class="global-open-sea-page h-full">
    <CrmCard no-content-padding hide-footer class="h-full">
      <div class="global-open-sea-page__body h-full min-h-0 px-[16px] pt-[16px]">
        <GlobalOpenSeaTable />
      </div>
    </CrmCard>
  </div>
</template>

<script setup lang="ts">
  import CrmCard from '@/components/pure/crm-card/index.vue';
  import GlobalOpenSeaTable from './components/globalOpenSeaTable.vue';
</script>

<style lang="less" scoped>
  .global-open-sea-page {
    :deep(.n-card),
    :deep(.n-card__content),
    .global-open-sea-page__body {
      display: flex;
      flex: 1;
      flex-direction: column;
      min-height: 0;
    }
  }
</style>
