<template>
  <div class="crm-customer-list-cell min-w-0">
    <CustomerListEditableTagCell
      v-if="isEditableInputMultipleTag"
      :column="column"
      :tags="row[column.key as string]"
      :editable="canEditTagCell"
      :loading="tagUpdating"
      @save="(tags) => emit('tagChange', tags)"
    />
    <CustomerListTagCell v-else-if="column.isTag" :column="column" :tags="row[column.key as string]" />
    <ListCellRender
      v-else-if="column.render"
      :column="column"
      :render="column.render"
      :row="row"
      :row-index="rowIndex ?? 0"
    />
    <CustomerListDateTimeCell v-else-if="isDateTimeColumn" :column="column" :value="row[column.key as string]" />
    <NEllipsis v-else class="customer-list-dynamic-cell" :tooltip="showEllipsisTooltip">
      {{ displayText }}
    </NEllipsis>
  </div>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import { NEllipsis } from 'naive-ui';

  import { FieldTypeEnum } from '@lib/shared/enums/formDesignEnum';

  import type { CrmDataTableColumn } from '@/components/pure/crm-table/type';
  import CustomerListDateTimeCell from './customerListDateTimeCell.vue';
  import CustomerListEditableTagCell from './customerListEditableTagCell.vue';
  import CustomerListTagCell from './customerListTagCell.vue';
  import ListCellRender from './listCellRender.vue';

  import { isCustomerListDateTimeColumn } from './customerListDateTime';

  const props = defineProps<{
    column: CrmDataTableColumn;
    row: Record<string, any>;
    rowIndex?: number;
    canEditTags?: boolean;
    editableTagFieldIds?: Set<string>;
    tagUpdating?: boolean;
  }>();

  const emit = defineEmits<{
    (e: 'tagChange', tags: string[]): void;
  }>();

  const isEditableInputMultipleTag = computed(
    () => props.column.isTag && props.column.filedType === FieldTypeEnum.INPUT_MULTIPLE
  );

  const canEditTagCell = computed(() => {
    if (!props.canEditTags || !isEditableInputMultipleTag.value) {
      return false;
    }
    const fieldId = props.column.fieldId || String(props.column.key ?? '');
    if (props.editableTagFieldIds && fieldId) {
      return props.editableTagFieldIds.has(fieldId);
    }
    return true;
  });

  const isDateTimeColumn = computed(() => isCustomerListDateTimeColumn(props.column));

  const displayText = computed(() => {
    const val = props.row[props.column.key as string];
    if (val === undefined || val === null || val === '') {
      return '-';
    }
    return String(val);
  });

  const showEllipsisTooltip = computed(() => {
    const { ellipsis } = props.column;
    if (ellipsis === true) return true;
    if (ellipsis && typeof ellipsis === 'object') {
      return ellipsis.tooltip !== false;
    }
    return true;
  });
</script>

<style lang="less" scoped>
  .customer-list-dynamic-cell {
    font-size: 14px;
    color: var(--text-n1);
  }
</style>
