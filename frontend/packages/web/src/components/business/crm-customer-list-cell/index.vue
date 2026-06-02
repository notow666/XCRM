<template>
  <div class="crm-customer-list-cell min-w-0">
    <span v-if="lightweight" class="customer-list-dynamic-cell one-line-text">{{ lightweightText }}</span>
    <template v-else>
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
    </template>
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

  import { isCustomerListDateTimeColumn, parseCustomerListDateTimeDisplay } from './customerListDateTime';
  import { formatCustomerListTagsPlain } from './customerListTag';

  const props = defineProps<{
    column: CrmDataTableColumn;
    row: Record<string, any>;
    rowIndex?: number;
    canEditTags?: boolean;
    editableTagFieldIds?: Set<string>;
    tagUpdating?: boolean;
    /** 纵向滚动中：纯文本单元格，跳过 NEllipsis / Tag 等重型组件 */
    lightweight?: boolean;
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

  const lightweightText = computed(() => {
    const key = props.column.key as string;
    const raw = props.row[key];

    if (props.column.isTag || isEditableInputMultipleTag.value) {
      return formatCustomerListTagsPlain(raw);
    }
    if (isDateTimeColumn.value) {
      const display = parseCustomerListDateTimeDisplay(
        raw,
        props.column.dateType ?? props.column.fieldConfig?.dateType
      );
      if (!display.dateLine || display.dateLine === '-') return '-';
      return display.timeLine ? `${display.dateLine} ${display.timeLine}` : display.dateLine;
    }
    if (props.column.render) {
      const result = props.column.render(props.row, props.rowIndex ?? 0);
      if (result === undefined || result === null || result === '') return '-';
      if (typeof result === 'string' || typeof result === 'number') return String(result);
      return '-';
    }
    return displayText.value;
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
