<template>
  <div
    :class="[
      'crm-customer-list-item flex w-full min-w-[800px] items-stretch border-b border-[var(--text-n8)] bg-[var(--text-n10)] transition-colors',
      'hover:bg-[var(--primary-7)]',
    ]"
    @click="emit('openDetail')"
  >
    <!-- 固定：客户信息 -->
    <div
      class="crm-customer-list-item__info flex shrink-0 items-center gap-[12px] border-r border-[var(--text-n8)] px-[16px] py-[12px]"
      :style="{ width: `${infoColumnWidth}px` }"
      @click.stop
    >
      <div v-if="showCheckbox" class="flex shrink-0 items-center self-center" @click.stop>
        <n-checkbox
          :checked="checked"
          :disabled="checkboxDisabled"
          @update:checked="(val: boolean) => emit('checkChange', val)"
        />
      </div>
      <div class="crm-customer-list-item__info-main min-w-0 flex-1">
        <div class="customer-list-item__name-row">
          <div class="customer-list-item__name-col min-w-0">
            <CrmNameTooltip
              v-if="limitShowDetail && !item.hasPermission"
              class="customer-list-item__name customer-list-item__name-ellipsis"
              :text="item.name"
            />
            <div
              v-else
              class="customer-list-item__name customer-list-item__name-ellipsis cursor-pointer"
              @click.stop="emit('openDetail')"
            >
              <span v-if="lightweight" class="one-line-text">{{ item.name || '-' }}</span>
              <NEllipsis v-else :tooltip="{ delay: 300 }">
                {{ item.name || '-' }}
              </NEllipsis>
            </div>
          </div>
          <div class="customer-list-item__mobile-col">
            <span class="customer-list-item__secondary one-line-text">{{ displayMobile || '\u00a0' }}</span>
          </div>
        </div>
        <div class="mt-[15px] space-y-[2px]">
          <p v-if="item.ownerName" class="customer-list-item__secondary one-line-text">
            {{ t('opportunity.owner') }}：{{ item.ownerName }}
          </p>
          <div class="customer-list-item__secondary customer-list-item__stage-row">
            <span
              class="customer-list-item__stage one-line-text"
              :style="{ color: stageTagStyle.color, lineHeight: '14px' }"
            >
              {{ stageStatusDisplay }}
            </span>
            <div v-if="showCustomerLevelStars" class="customer-list-item__stars-col">
              <CustomerListLevelStars
                :level="customerLevelStarCount"
                :editable="!lightweight && canEditCustomerLevel"
                :loading="levelUpdating"
                @select="(level) => emit('customerLevelChange', level)"
              />
            </div>
          </div>
        </div>
      </div>
      <div
        v-if="showReach"
        class="crm-customer-list-item__reach shrink-0 self-center"
        :class="{ 'crm-customer-list-item__reach--hidden': lightweight }"
        :aria-hidden="lightweight"
      >
        <slot v-if="!lightweight" name="reach"></slot>
      </div>
    </div>

    <!-- 动态列（横向滚动与表头同步，由父级统一控制） -->
    <div class="crm-customer-list-item__middle flex min-w-0 flex-1 overflow-hidden" @wheel="handleMiddleWheel">
      <div class="crm-customer-list-item__middle-track flex h-full" :style="middleTrackStyle">
        <div
          v-for="column in middleColumns"
          :key="String(column.key)"
          class="crm-customer-list-item__col customer-list-dynamic-td flex shrink-0 flex-col justify-center"
          :style="{ width: `${getColumnWidth(column)}px`, minWidth: `${getColumnWidth(column)}px` }"
        >
          <CrmCustomerListCell
            :column="column"
            :row="item"
            :row-index="rowIndex"
            :lightweight="lightweight"
            :can-edit-tags="canEditTags"
            :editable-tag-field-ids="editableTagFieldIds"
            :tag-updating="
              !!tagUpdatingRowId &&
              tagUpdatingRowId === item.id &&
              tagUpdatingFieldId === (column.fieldId || String(column.key ?? ''))
            "
            @tag-change="(tags) => handleTagChange(column, tags)"
          />
        </div>
        <div
          v-if="!middleColumns.length"
          class="flex flex-1 items-center px-[16px] py-[12px] text-[12px] text-[var(--text-n4)]"
        >
          -
        </div>
      </div>
    </div>

    <!-- 固定：操作（跟进/编辑 | 转移/更多） -->
    <div
      class="crm-customer-list-item__operation flex shrink-0 items-center justify-center border-l border-[var(--text-n8)] px-[8px] py-[10px]"
      :style="{ width: `${operationColumnWidth}px` }"
      @click.stop
    >
      <div v-if="showOperation" class="operation-grid w-full">
        <div class="operation-grid__row">
          <n-button
            text
            type="primary"
            class="operation-grid__btn !h-auto text-[14px]"
            :disabled="isFollowUpDisabled"
            @click="emit('operationSelect', 'followUp')"
          >
            {{ t('opportunity.followUp') }}
          </n-button>
          <n-button
            text
            type="primary"
            class="operation-grid__btn !h-auto text-[14px]"
            :disabled="!canEdit"
            @click="emit('operationSelect', 'edit')"
          >
            {{ t('common.edit') }}
          </n-button>
        </div>
        <div class="operation-grid__row">
          <n-button
            text
            type="primary"
            class="operation-grid__btn !h-auto text-[14px]"
            :disabled="!canTransfer"
            @click="emit('operationSelect', 'transfer')"
          >
            {{ t('common.transfer') }}
          </n-button>
          <CrmMoreAction
            v-if="visibleMoreActions.length"
            :options="visibleMoreActions"
            placement="bottom-end"
            @select="(action) => emit('operationSelect', action.key as string)"
          >
            <template #default>
              <n-button text type="primary" class="operation-grid__btn !h-auto text-[14px]">
                {{ t('common.more') }}
              </n-button>
            </template>
          </CrmMoreAction>
          <n-button v-else text type="primary" class="operation-grid__btn !h-auto text-[14px]" disabled>
            {{ t('common.more') }}
          </n-button>
        </div>
      </div>
      <span v-else class="text-[12px] text-[var(--text-n4)]">-</span>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { NButton, NCheckbox, NEllipsis } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';

  import CrmMoreAction from '@/components/pure/crm-more-action/index.vue';
  import type { ActionsItem } from '@/components/pure/crm-more-action/type';
  import CrmNameTooltip from '@/components/pure/crm-name-tooltip/index.vue';
  import type { CrmDataTableColumn } from '@/components/pure/crm-table/type';
  import CrmCustomerListCell from '@/components/business/crm-customer-list-cell/index.vue';
  import CustomerListLevelStars from '@/components/business/crm-customer-list-item/customerListLevelStars.vue';

  import {
    formatCustomerStageStatusLabel,
    getCustomerLevelStarCount,
    getCustomerStageTagStyle,
  } from '@/hooks/useCustomerListDescription';
  import { hasAllPermission, hasAnyPermission } from '@/utils/permission';

  const props = withDefaults(
    defineProps<{
      item: Record<string, any>;
      middleColumns?: CrmDataTableColumn[];
      rowIndex?: number;
      showCheckbox?: boolean;
      checked?: boolean;
      checkboxDisabled?: boolean;
      limitShowDetail?: boolean;
      showOperation?: boolean;
      showReach?: boolean;
      followUpDisabled?: boolean;
      hideEditTransfer?: boolean;
      operationMoreList?: ActionsItem[];
      infoColumnWidth?: number;
      operationColumnWidth?: number;
      middleTotalWidth?: number;
      stageConfigList?: Array<{ id: string; type?: string; rate?: string | number }>;
      customerLevelFieldId?: string;
      levelUpdating?: boolean;
      canEditTags?: boolean;
      editableTagFieldIds?: Set<string>;
      tagUpdatingRowId?: string;
      tagUpdatingFieldId?: string;
      /** 纵向滚动中启用轻量渲染 */
      lightweight?: boolean;
    }>(),
    {
      middleColumns: () => [],
      rowIndex: 0,
      infoColumnWidth: 280,
      operationColumnWidth: 120,
      middleTotalWidth: 0,
      showReach: true,
      followUpDisabled: false,
      stageConfigList: () => [],
    }
  );

  const emit = defineEmits<{
    (e: 'openDetail'): void;
    (e: 'checkChange', checked: boolean): void;
    (e: 'operationSelect', key: string): void;
    (e: 'dynamicWheel', event: WheelEvent): void;
    (e: 'customerLevelChange', level: number): void;
    (e: 'tagChange', fieldId: string, tags: string[]): void;
  }>();

  function handleTagChange(column: CrmDataTableColumn, tags: string[]) {
    const fieldId = column.fieldId || String(column.key ?? '');
    if (!fieldId) return;
    emit('tagChange', fieldId, tags);
  }

  const middleTrackStyle = computed(() => ({
    width: `${props.middleTotalWidth}px`,
  }));

  function handleMiddleWheel(event: WheelEvent) {
    emit('dynamicWheel', event);
  }

  const { t } = useI18n();

  const displayMobile = computed(() => {
    const { mobile } = props.item;
    if (!mobile) return '';
    return String(mobile);
  });

  const showCustomerLevelStars = computed(() => !!props.customerLevelFieldId);

  const stageStatusDisplay = computed(
    () => formatCustomerStageStatusLabel(props.item, t('customer.invalidStageCustomer')) || '-'
  );

  const stageTagStyle = computed(() => getCustomerStageTagStyle(props.item.stage, props.stageConfigList));

  const customerLevelStarCount = computed(() => getCustomerLevelStarCount(props.item, props.customerLevelFieldId));

  const isFollowUpDisabled = computed(
    () => props.followUpDisabled || !hasAnyPermission(['CUSTOMER_MANAGEMENT:UPDATE'])
  );

  const canEdit = computed(() => !props.hideEditTransfer && hasAnyPermission(['CUSTOMER_MANAGEMENT:UPDATE']));

  /** 与操作列「编辑」按钮一致：canEdit 为 true 时可点击修改等级 */
  const canEditCustomerLevel = computed(() => !!props.customerLevelFieldId && canEdit.value && !props.levelUpdating);

  const canTransfer = computed(() => !props.hideEditTransfer && hasAnyPermission(['CUSTOMER_MANAGEMENT:TRANSFER']));

  function getColumnWidth(column: CrmDataTableColumn) {
    const { width: w } = column;
    if (typeof w === 'number') return Math.max(w, 80);
    return 120;
  }

  function filterActions(list: ActionsItem[] = []) {
    return list.filter((item) => {
      if (!item.permission?.length) return true;
      return item.allPermission ? hasAllPermission(item.permission) : hasAnyPermission(item.permission);
    });
  }

  const visibleMoreActions = computed(() => filterActions(props.operationMoreList));
</script>

<style lang="less" scoped>
  /** 列表触达区纵向三图标宽度（与 CrmCustomerListReach direction=column、icon 22px 一致） */
  @reach-slot-width: 22px;

  .crm-customer-list-item {
    cursor: pointer;
  }
  .crm-customer-list-item__info-main {
    min-width: 0;
  }
  .crm-customer-list-item__reach {
    width: @reach-slot-width;
    min-width: @reach-slot-width;
  }
  .crm-customer-list-item__reach--hidden {
    visibility: hidden;
    pointer-events: none;
  }
  .customer-list-item__name-col {
    overflow: hidden;
    min-width: 0;
  }
  .customer-list-item__name {
    font-size: 16px;
    font-weight: 600;
    line-height: 22px;
    color: var(--n-td-text-color);

    :deep(.n-ellipsis),
    :deep(.one-line-text) {
      font-size: inherit;
      font-weight: inherit;
      line-height: inherit;
      color: inherit;
    }
  }
  .customer-list-item__name-ellipsis {
    display: block;
    width: 100%;
    min-width: 0;
    max-width: 100%;

    :deep(.n-ellipsis) {
      display: block;
      max-width: 100%;
    }
    :deep(.one-line-text) {
      display: block;
      max-width: 100%;
    }
  }
  .customer-list-item__secondary {
    font-size: 12px;
    line-height: 18px;
    color: var(--text-n3);
  }
  /* 名称/手机号、阶段/星星：固定列宽，避免随前置文案长短错位 */
  .customer-list-item__name-row,
  .customer-list-item__stage-row {
    display: grid;
    column-gap: 8px;
    align-items: center;
  }
  .customer-list-item__name-row {
    grid-template-columns: minmax(0, 1fr) 80px 20px;
    align-items: baseline;
  }
  .customer-list-item__stage-row {
    grid-template-columns: minmax(0, 1fr) 82px 20px;
  }
  .customer-list-item__mobile-col {
    min-width: 80px;
    text-align: right;
    white-space: nowrap;
  }
  .customer-list-item__stars-col {
    display: flex;
    justify-content: flex-end;
    align-items: center;
    min-width: 82px;
    min-height: 14px;
  }
  .customer-list-item__stage {
    font-weight: 500;
    min-width: 0;
  }
  .customer-list-item__stage--placeholder {
    display: block;
    min-height: 18px;
  }
  .crm-customer-list-item__middle {
    flex: 1;
    min-width: 0;
  }
  .crm-customer-list-item__middle-track {
    flex-shrink: 0;
    transform: translateX(calc(-1 * var(--customer-list-scroll-left, 0px)));
  }
  .operation-grid {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }
  .operation-grid__row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 4px;
  }
  .operation-grid__btn {
    flex: 1;
    min-width: 0;
    justify-content: center;
    padding: 0 2px !important;
    --n-text-color: #00a6ab;
    --n-text-color-hover: #00a6ab;
    --n-text-color-pressed: #008d91;
    --n-text-color-focus: #00a6ab;
    --n-text-color-disabled: #b2e4e6;
  }
</style>
