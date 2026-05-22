<template>
  <n-popover
    v-if="editable"
    v-model:show="popoverVisible"
    trigger="click"
    placement="bottom-start"
    :show-arrow="false"
    to="body"
    :disabled="loading"
    @update:show="handlePopoverShowChange"
  >
    <template #trigger>
      <div
        class="customer-list-editable-tag-cell"
        :class="{ 'customer-list-editable-tag-cell--editable': editable && !loading }"
        @click.stop
      >
        <CustomerListTagCell :column="column" :tags="tags" />
      </div>
    </template>
    <div class="customer-list-editable-tag-cell__editor" @click.stop>
      <n-select
        ref="selectRef"
        v-model:value="draftTags"
        class="customer-list-editable-tag-cell__select"
        filterable
        multiple
        tag
        :placeholder="t('common.tagsInputPlaceholder')"
        :show-arrow="false"
        :show="false"
        :disabled="loading"
        :input-props="{ maxlength: 64 }"
        :fallback-option="draftTags.length <= 10 ? fallbackOption : false"
        :render-tag="renderTag"
        clearable
        @keydown.enter="handleInputEnter"
      />
      <div class="customer-list-editable-tag-cell__actions">
        <n-button size="small" :disabled="loading" @click="handleCancel">
          {{ t('common.cancel') }}
        </n-button>
        <n-button size="small" type="primary" :loading="loading" :disabled="!hasChanges" @click="handleConfirm">
          {{ t('common.confirm') }}
        </n-button>
      </div>
    </div>
  </n-popover>
  <CustomerListTagCell v-else :column="column" :tags="tags" />
</template>

<script setup lang="ts">
  import { computed, h, ref, watch } from 'vue';
  import { NButton, NPopover, NSelect, NTag, NTooltip, useMessage } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';

  import type { CrmDataTableColumn } from '@/components/pure/crm-table/type';
  import CustomerListTagCell from './customerListTagCell.vue';

  import type { SelectBaseOption } from 'naive-ui/es/select/src/interface';

  const TAG_MAX_COUNT = 10;

  const props = withDefaults(
    defineProps<{
      column: CrmDataTableColumn;
      tags: string[] | Record<string, any>[] | null | undefined;
      editable?: boolean;
      loading?: boolean;
    }>(),
    {
      editable: false,
      loading: false,
    }
  );

  const emit = defineEmits<{
    (e: 'save', tags: string[]): void;
  }>();

  const { t } = useI18n();
  const Message = useMessage();

  const popoverVisible = ref(false);
  const draftTags = ref<string[]>([]);
  const selectRef = ref<InstanceType<typeof NSelect>>();

  const normalizedTags = computed(() => {
    if (!props.tags?.length) return [];
    return props.tags
      .map((item) => {
        if (typeof item === 'string') return item;
        const label = item?.label ?? item?.name;
        return label === undefined || label === null ? '' : String(label);
      })
      .filter(Boolean);
  });

  const hasChanges = computed(() => {
    const current = normalizedTags.value;
    const draft = draftTags.value;
    if (current.length !== draft.length) return true;
    return current.some((tag, index) => tag !== draft[index]);
  });

  function syncDraftFromTags() {
    draftTags.value = [...normalizedTags.value];
  }

  watch(
    () => props.tags,
    () => {
      if (!popoverVisible.value) {
        syncDraftFromTags();
      }
    },
    { deep: true }
  );

  function handlePopoverShowChange(show: boolean) {
    if (show) {
      syncDraftFromTags();
    }
  }

  function fallbackOption(val: string | number) {
    return {
      label: `${val}`,
      value: val,
    };
  }

  function renderTag({ option, handleClose }: { option: SelectBaseOption; handleClose: () => void }) {
    return h(
      NTooltip,
      {},
      {
        default: () => h('div', {}, { default: () => option.label }),
        trigger: () => h(NTag, { closable: true, onClose: handleClose }, { default: () => option.label }),
      }
    );
  }

  function handleInputEnter() {
    if (draftTags.value.length > TAG_MAX_COUNT) {
      draftTags.value = draftTags.value.slice(0, TAG_MAX_COUNT);
      Message.warning(t('crmFormCreate.basic.tagInputLimitTip'));
      return;
    }
    const inputEl = selectRef.value?.$el?.querySelector(
      '.n-base-selection-input-tag__input'
    ) as HTMLInputElement | null;
    const inputValue = inputEl?.value?.trim();
    if (inputValue && draftTags.value.includes(inputValue)) {
      Message.warning(t('crmFormCreate.basic.tagInputRepeatTip'));
    }
  }

  function handleCancel() {
    syncDraftFromTags();
    popoverVisible.value = false;
  }

  function handleConfirm() {
    if (props.loading) {
      return;
    }
    if (!hasChanges.value) {
      popoverVisible.value = false;
      return;
    }
    emit('save', [...draftTags.value]);
  }

  watch(
    () => [props.loading, normalizedTags.value.join('\u0001')] as const,
    ([loading], [prevLoading]) => {
      if (prevLoading && !loading && popoverVisible.value) {
        const draftKey = draftTags.value.join('\u0001');
        const currentKey = normalizedTags.value.join('\u0001');
        if (draftKey === currentKey) {
          popoverVisible.value = false;
        }
      }
    }
  );
</script>

<style lang="less" scoped>
  .customer-list-editable-tag-cell {
    width: 100%;
    min-width: 0;
    &--editable {
      cursor: pointer;
      border-radius: 4px;
      &:hover {
        background-color: var(--primary-7);
      }
    }
  }
  .customer-list-editable-tag-cell__editor {
    width: 280px;
    padding: 4px 0;
  }
  .customer-list-editable-tag-cell__select {
    width: 100%;
  }
  .customer-list-editable-tag-cell__actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 8px;
  }
</style>
