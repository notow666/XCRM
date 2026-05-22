<template>
  <div class="customer-list-tag-cell">
    <span v-if="!tagItems.length" class="customer-list-tag-cell__empty">-</span>
    <div v-else class="customer-list-tag-cell__list">
      <CrmTag
        v-for="(item, index) in visibleTags"
        :key="`tag-${index}`"
        v-bind="tagBindProps"
        class="customer-list-tag-cell__tag"
        :tooltip-disabled="item.fullLabel === item.displayLabel"
      >
        <template v-if="item.fullLabel !== item.displayLabel" #tooltipContent>
          {{ item.fullLabel }}
        </template>
        {{ item.displayLabel }}
      </CrmTag>
      <n-tooltip v-if="hiddenTags.length" trigger="hover" :delay="300" placement="top">
        <template #trigger>
          <CrmTag v-bind="tagBindProps" class="customer-list-tag-cell__tag" :tooltip-disabled="true">
            +{{ hiddenTags.length }}
          </CrmTag>
        </template>
        {{ hiddenTooltip }}
      </n-tooltip>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import { NTooltip } from 'naive-ui';

  import type { CrmDataTableColumn } from '@/components/pure/crm-table/type';
  import CrmTag from '@/components/pure/crm-tag/index.vue';

  import {
    getCustomerListTagBindProps,
    normalizeCustomerListTags,
    splitCustomerListVisibleTags,
  } from './customerListTag';

  const props = defineProps<{
    column: CrmDataTableColumn;
    tags: string[] | Record<string, any>[] | null | undefined;
  }>();

  const labelKey = computed(() => props.column.tagGroupProps?.labelKey ?? 'label');

  const tagBindProps = computed(() => getCustomerListTagBindProps(props.column.tagGroupProps));

  const tagItems = computed(() => normalizeCustomerListTags(props.tags, labelKey.value));

  const visibleSplit = computed(() => splitCustomerListVisibleTags(tagItems.value));

  const visibleTags = computed(() => visibleSplit.value.visible);

  const hiddenTags = computed(() => visibleSplit.value.hidden);

  const hiddenTooltip = computed(() => hiddenTags.value.map((item) => item.fullLabel).join('，'));
</script>

<style lang="less" scoped>
  .customer-list-tag-cell {
    width: 100%;
    min-width: 0;
  }
  .customer-list-tag-cell__empty {
    font-size: 14px;
    color: var(--text-n1);
  }
  .customer-list-tag-cell__list {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
    width: 100%;
    min-width: 0;
  }
  .customer-list-tag-cell__tag {
    max-width: 100%;
    :deep(.n-tag) {
      max-width: 100%;
    }
    :deep(.one-line-text) {
      max-width: 5em;
    }
  }
</style>
