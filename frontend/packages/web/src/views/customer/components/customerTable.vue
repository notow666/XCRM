<template>
  <CrmTable
    ref="crmTableRef"
    v-model:checked-row-keys="checkedRowKeys"
    v-bind="tableBindProps"
    class="crm-customer-table h-full min-h-0"
    :columns="tableColumns"
    :not-show-table="useListLayout"
    :not-show-table-filter="useListLayout || isAdvancedSearchMode"
    :action-config="props.readonly ? undefined : actionConfig"
    @change-columns-setting="handleListColumnsSettingChange"
    @row-key-change="handleRowKeyChange"
    @page-change="propsEvent.pageChange"
    @page-size-change="propsEvent.pageSizeChange"
    @sorter-change="propsEvent.sorterChange"
    @filter-change="propsEvent.filterChange"
    @batch-action="handleBatchAction"
    @refresh="searchData"
  >
    <template v-if="props.readonly" #tableTop>
      <slot name="searchTableTotal" :total="propsRes.crmPagination?.itemCount || 0"></slot>
    </template>
    <template #actionLeft>
      <div class="flex items-center gap-[12px]">
        <n-button
          v-if="
            activeTab !== CustomerSearchTypeEnum.CUSTOMER_COLLABORATION &&
            hasAnyPermission(['CUSTOMER_MANAGEMENT:ADD']) &&
            !props.readonly
          "
          type="primary"
          @click="handleNewClick"
        >
          {{ t('customer.new') }}
        </n-button>
        <CrmImportButton
          v-if="hasAnyPermission(['CUSTOMER_MANAGEMENT:IMPORT'])"
          :api-type="FormDesignKeyEnum.CUSTOMER"
          :title="t('module.customerManagement')"
          @import-success="() => searchData()"
        />
        <n-button
          v-if="
            activeTab !== CustomerSearchTypeEnum.CUSTOMER_COLLABORATION &&
            hasAnyPermission(['CUSTOMER_MANAGEMENT:TRANSFER']) &&
            !props.readonly
          "
          type="primary"
          ghost
          class="n-btn-outline-primary"
          :disabled="(propsRes.crmPagination?.itemCount || 0) === 0"
          @click="handleTransferByConditionClick"
        >
          {{ t('customer.transferByCondition') }}
        </n-button>
        <n-button
          v-if="
            activeTab !== CustomerSearchTypeEnum.CUSTOMER_COLLABORATION &&
            hasAnyPermission(['CUSTOMER_MANAGEMENT:RECYCLE']) &&
            !props.readonly
          "
          type="primary"
          ghost
          class="n-btn-outline-primary"
          :disabled="(propsRes.crmPagination?.itemCount || 0) === 0"
          @click="handleMoveToOpenSeaByConditionClick"
        >
          {{ t('customer.moveToOpenSeaByCondition') }}
        </n-button>
        <n-button
          v-if="
            activeTab !== CustomerSearchTypeEnum.CUSTOMER_COLLABORATION &&
            hasAnyPermission(['CUSTOMER_MANAGEMENT:UPDATE']) &&
            !props.readonly
          "
          type="primary"
          ghost
          class="n-btn-outline-primary"
          :disabled="(propsRes.crmPagination?.itemCount || 0) === 0"
          @click="handleEditByConditionClick"
        >
          {{ t('customer.editByCondition') }}
        </n-button>
        <n-button
          v-if="
            hasAnyPermission(['CUSTOMER_MANAGEMENT:DELETE']) &&
            activeTab !== CustomerSearchTypeEnum.CUSTOMER_COLLABORATION &&
            !props.readonly
          "
          type="error"
          ghost
          :disabled="(propsRes.crmPagination?.itemCount || 0) === 0"
          @click="handleDeleteByCondition"
        >
          {{ t('customer.deleteByCondition') }}
        </n-button>
        <n-button
          v-if="
            hasAnyPermission(['CUSTOMER_MANAGEMENT:EXPORT']) &&
            activeTab !== CustomerSearchTypeEnum.CUSTOMER_COLLABORATION &&
            !props.readonly
          "
          type="primary"
          ghost
          class="n-btn-outline-primary"
          :disabled="propsRes.data.length === 0"
          @click="handleExportAllClick"
        >
          {{ t('common.exportAll') }}
        </n-button>
      </div>
    </template>
    <template #actionRight>
      <CrmAdvanceFilter
        v-if="!props.hiddenAdvanceFilter"
        ref="tableAdvanceFilterRef"
        v-model:keyword="keyword"
        :custom-fields-config-list="customerAdvancedFilterConfig"
        :filter-config-list="filterConfigList"
        @adv-search="handleAdvSearch"
        @keyword-search="searchData"
      />
    </template>
    <template #view>
      <CrmViewSelect
        v-if="!props.hiddenAdvanceFilter"
        v-model:active-tab="activeTab"
        :type="FormDesignKeyEnum.CUSTOMER"
        :custom-fields-config-list="customerAdvancedFilterConfig"
        :filter-config-list="filterConfigList"
        :advanced-original-form="advancedOriginalForm"
        :route-name="CustomerRouteEnum.CUSTOMER_INDEX"
        @refresh-table-data="searchData"
        @generated-chart="handleGeneratedChart"
      />
    </template>
    <template v-if="useListLayout" #other>
      <div
        class="customer-list-panel flex min-h-0 flex-1 flex-col overflow-hidden"
        :style="{ '--customer-list-scroll-left': `${dynamicScrollLeft}px` }"
      >
        <div class="customer-list-table-header b-0 flex shrink-0 border border-[var(--text-n8)]">
          <div
            class="customer-list-dynamic-th customer-list-table-header__info flex shrink-0 items-center border-r border-[var(--text-n8)]"
            :style="{ width: `${listInfoColumnWidth}px` }"
          >
            <n-checkbox
              v-if="showListCheckbox"
              class="mr-[8px]"
              :checked="listHeaderCheckState.checked"
              :indeterminate="listHeaderCheckState.indeterminate"
              :disabled="!listSelectableIds.length"
              @update:checked="handleListSelectAll"
            />
            <span class="one-line-text">{{ t('customer.list.customerInfo') }}</span>
          </div>
          <div
            class="customer-list-table-header__middle flex min-w-0 flex-1 overflow-hidden"
            @wheel="handleDynamicWheel"
          >
            <div class="customer-list-dynamic-header-track flex" :style="dynamicMiddleTrackStyle">
              <CrmCustomerListDynamicTh
                v-for="column in listMiddleColumns"
                :key="String(column.key)"
                :column="column"
                :title="getListColumnTitle(column)"
                :width="getListColumnWidth(column)"
                @sort="handleListColumnSort"
              />
            </div>
          </div>
          <div
            class="customer-list-dynamic-th customer-list-table-header__operation flex shrink-0 items-center justify-center border-l border-[var(--text-n8)] text-center"
            :style="{ width: `${listOperationColumnWidth}px` }"
          >
            <span class="one-line-text">{{ t('common.operation') }}</span>
          </div>
        </div>
        <CrmList
          v-if="propsRes.data.length"
          ref="crmListRef"
          :key="`${String(activeTab ?? '')}-${customerListRenderKey}`"
          v-model:data="propsRes.data"
          key-field="id"
          mode="remote"
          :item-height="listItemHeight"
          :loading="!!propsRes.loading"
          :loading-mode="listLoadingMode"
          :no-more-data="isCustomerListNoMoreData"
          virtual-scroll-height="100%"
          class="customer-list-scroll min-h-0 flex-1"
          @reach-bottom="handleListReachBottom"
          @virtual-scroll="handleCustomerListVirtualScroll"
        >
          <template #item="{ item }">
            <div
              :key="item.id"
              class="customer-list-item-wrap"
              :class="{ 'customer-list-item-wrap--enter': isListRowEnter(item.id) }"
              :style="{ height: `${listItemHeight}px` }"
              @animationend="clearEnterRowId(String(item.id))"
            >
              <CrmCustomerListItem
                v-memo="[
                  item.id,
                  checkedIdSet.has(item.id),
                  listMiddleColumns.length,
                  isListScrolling,
                  updatingCustomerLevelId === item.id,
                  updatingCustomerTag?.rowId === item.id && updatingCustomerTag?.fieldId,
                ]"
                :lightweight="isListScrolling"
                :item="item"
                :row-index="0"
                :middle-columns="listMiddleColumns"
                :middle-total-width="listMiddleTotalWidth"
                :info-column-width="listInfoColumnWidth"
                :operation-column-width="listOperationColumnWidth"
                :show-checkbox="showListCheckbox"
                :show-reach="showReachColumn"
                :checked="checkedIdSet.has(item.id)"
                :checkbox-disabled="item.collaborationType === 'READ_ONLY'"
                :limit-show-detail="!!props.isLimitShowDetail"
                :show-operation="showListItemOperation(item)"
                :follow-up-disabled="excludeStageIds.includes(item.stage)"
                :stage-config-list="stageConfig?.stageConfigList ?? []"
                :customer-level-field-id="customerLevelFieldId"
                :level-updating="updatingCustomerLevelId === item.id"
                :can-edit-tags="canEditListTags"
                :editable-tag-field-ids="editableTagFieldIds"
                :tag-updating-row-id="updatingCustomerTag?.rowId"
                :tag-updating-field-id="updatingCustomerTag?.fieldId"
                :hide-edit-transfer="activeTab === CustomerSearchTypeEnum.CUSTOMER_COLLABORATION"
                :operation-more-list="resolveListOperationMoreList(item)"
                @open-detail="() => handleOpenCustomerDetail(item)"
                @check-change="(checked) => handleListItemCheckChange(item, checked)"
                @operation-select="(key) => handleActionSelect(item, key)"
                @customer-level-change="(level) => handleCustomerLevelChange(item, level)"
                @tag-change="(fieldId, tags) => handleCustomerTagChange(item, fieldId, tags)"
                @dynamic-wheel="handleDynamicWheel"
              >
                <template v-if="showReachColumn" #reach>
                  <CrmCustomerListReach
                    :call-status="readCallStatus(item)"
                    :wechat-friend-status="readWechatFriendStatus(item)"
                    :can-operate="isReachOwner(item)"
                    @dial="(slot) => handleDialCustomer(item, slot)"
                    @sms="(slot) => handleSmsCustomer(item, slot)"
                    @wechat="() => handleWechatCustomer(item)"
                    @add-wechat="() => handleWechatFriendCustomer(item)"
                  />
                </template>
              </CrmCustomerListItem>
            </div>
          </template>
        </CrmList>
        <div
          v-if="useListLayout && propsRes.data.length"
          ref="customerListScrollbarTrackRef"
          class="customer-list-vertical-scrollbar"
          :class="{ 'customer-list-vertical-scrollbar--visible': showCustomerListScrollbar }"
          @pointerdown="handleCustomerListScrollbarPointerDown"
        >
          <div class="customer-list-vertical-scrollbar__thumb" :style="customerListScrollbarThumbStyle" />
        </div>
        <div
          v-else-if="propsRes.loading"
          class="customer-list-panel__loading flex min-h-0 flex-1 items-center justify-center border border-t-0 border-[var(--text-n8)] py-[48px]"
        >
          <n-spin size="medium" />
        </div>
        <div
          v-else
          class="customer-list-panel__empty flex min-h-0 flex-1 items-center justify-center border border-t-0 border-[var(--text-n8)] py-[48px] text-[14px] text-[var(--text-n4)]"
        >
          {{ t('common.noData') }}
        </div>
        <div
          v-if="propsRes.data.length && listMiddleColumns.length"
          class="customer-list-dynamic-scrollbar-row flex shrink-0"
        >
          <div class="shrink-0" :style="{ width: `${listInfoColumnWidth}px` }" />
          <div
            ref="dynamicScrollRef"
            class="customer-list-dynamic-scrollbar min-w-0 flex-1"
            @scroll="handleDynamicScroll"
          >
            <div class="customer-list-dynamic-scrollbar__inner" :style="{ width: `${listMiddleTotalWidth}px` }" />
          </div>
          <div class="shrink-0" :style="{ width: `${listOperationColumnWidth}px` }" />
        </div>
      </div>
    </template>
  </CrmTable>

  <CrmTransferCustomerModal
    v-model:show="showSingleTransferModal"
    :source-ids="checkedRowKeys"
    :save-api="batchTransferCustomer"
    :title="t('common.transfer')"
    @load-list="searchData"
  />
  <CrmCustomerTransferByConditionModal
    v-model:show="showTransferByConditionModal"
    :total="propsRes.crmPagination?.itemCount || 0"
    :query-params="transferByConditionQueryParams"
    @success="handleTransferByConditionSuccess"
  />
  <CrmCustomerToPoolByConditionModal
    v-model:show="showToPoolByConditionModal"
    :total="propsRes.crmPagination?.itemCount || 0"
    :query-params="transferByConditionQueryParams"
    @success="handleToPoolByConditionSuccess"
  />
  <CrmCustomerBatchEditByConditionModal
    v-model:show="showEditByConditionModal"
    :total="propsRes.crmPagination?.itemCount || 0"
    :query-params="transferByConditionQueryParams"
    :field-list="fieldList"
    :form-key="FormDesignKeyEnum.CUSTOMER"
    @success="handleEditByConditionSuccess"
  />
  <customerOverviewDrawer
    v-model:show="showOverviewDrawer"
    :source-id="activeSourceId"
    :navigation-rows="overviewNavigationRows"
    :navigation-total="navigationTotal"
    :navigation-has-more="navigationHasMore"
    :navigation-loading="!!propsRes.loading"
    :on-navigation-load-more="handleNavigationLoadMore"
    @update:source-id="activeSourceId = $event"
    @saved="searchData(undefined, activeSourceId)"
    @deleted="removeItemFromList(activeSourceId)"
    @transfer="searchData"
  />
  <CrmFormCreateDrawer
    v-model:visible="formCreateDrawerVisible"
    :form-key="activeFormKey"
    :source-id="activeSourceId"
    :need-init-detail="needInitDetail"
    :initial-source-name="initialSourceName"
    :other-save-params="otherFollowRecordSaveParams"
    :link-form-info="linkFormFieldMap"
    :link-form-key="FormDesignKeyEnum.CUSTOMER"
    :link-scenario="
      activeFormKey === FormDesignKeyEnum.FOLLOW_RECORD_CUSTOMER ? FormLinkScenarioEnum.CUSTOMER_TO_RECORD : undefined
    "
    @saved="handleFormCreateSaved"
  />
  <CrmFormCreateDrawer
    v-model:visible="planFormDrawerVisible"
    :form-key="FormDesignKeyEnum.FOLLOW_PLAN_CUSTOMER"
    :other-save-params="planFormSaveParams"
    @saved="handlePlanSaved"
  />
  <CrmTableExportModal
    v-model:show="showExportModal"
    :params="exportParams"
    :export-columns="exportColumns"
    :is-export-all="isExportAll"
    type="customer"
    @create-success="handleExportCreateSuccess"
  />
  <CrmMoveModal
    v-model:show="showMoveModal"
    :reason-key="ReasonTypeEnum.CUSTOMER_POOL_RS"
    :source-id="moveIds"
    :name="initialSourceName"
    :pool-id="selectedPoolId"
    type="warning"
    @refresh="handleMoveRefresh"
  />
  <CrmSelectPoolModal v-model:show="showSelectPoolModal" :name="initialSourceName" @confirm="handlePoolSelected" />

  <customerSmsModal
    v-model:show="showReachModal"
    :mode="reachModal.mode"
    :source-id="reachModal.sourceId"
    :name="reachModal.name"
    :mobile="reachModal.mobile"
    :wechat-options="activeWechatOptions"
    @submit="handleReachModalSubmit"
  />
</template>

<script setup lang="ts">
  import { useRoute } from 'vue-router';
  import { DataTableRowKey, NButton, NCheckbox, NDropdown, NIcon, NSpin, useMessage } from 'naive-ui';
  import { LogoWechat } from '@vicons/ionicons5';

  import {
    CUSTOMER_BATCH_BY_CONDITION_DOM_EVENT,
    type CustomerBatchByConditionSseDetail,
  } from '@lib/shared/constants/sseEventType';
  import { CustomerSearchTypeEnum } from '@lib/shared/enums/customerEnum';
  import { FieldTypeEnum, FormDesignKeyEnum, FormLinkScenarioEnum } from '@lib/shared/enums/formDesignEnum';
  import { ReasonTypeEnum } from '@lib/shared/enums/moduleEnum';
  import { SpecialColumnEnum, TableKeyEnum } from '@lib/shared/enums/tableEnum';
  import { useI18n } from '@lib/shared/hooks/useI18n';
  import useLocale from '@lib/shared/locale/useLocale';
  import { characterLimit } from '@lib/shared/method';
  import { ExportTableColumnItem } from '@lib/shared/models/common';

  import CrmAdvanceFilter from '@/components/pure/crm-advance-filter/index.vue';
  import { FilterForm, FilterFormItem, FilterResult } from '@/components/pure/crm-advance-filter/type';
  import CrmIcon from '@/components/pure/crm-icon-font/index.vue';
  import CrmList from '@/components/pure/crm-list/index.vue';
  import type { ActionsItem } from '@/components/pure/crm-more-action/type';
  import CrmNameTooltip from '@/components/pure/crm-name-tooltip/index.vue';
  import CrmTable from '@/components/pure/crm-table/index.vue';
  import { BatchActionConfig, type CrmDataTableColumn } from '@/components/pure/crm-table/type';
  import CrmTableButton from '@/components/pure/crm-table-button/index.vue';
  import CrmCustomerBatchEditByConditionModal from '@/components/business/crm-customer-batch-edit-by-condition-modal/index.vue';
  import CrmCustomerListDynamicTh from '@/components/business/crm-customer-list-dynamic-th/index.vue';
  import CrmCustomerListItem from '@/components/business/crm-customer-list-item/index.vue';
  import CrmCustomerListReach from '@/components/business/crm-customer-list-reach/index.vue';
  import CrmCustomerToPoolByConditionModal from '@/components/business/crm-customer-to-pool-by-condition-modal/index.vue';
  import CrmCustomerTransferByConditionModal from '@/components/business/crm-customer-transfer-by-condition-modal/index.vue';
  import CrmFormCreateDrawer from '@/components/business/crm-form-create-drawer/index.vue';
  import CrmImportButton from '@/components/business/crm-import-button/index.vue';
  import CrmMoveModal from '@/components/business/crm-move-modal/index.vue';
  import CrmOperationButton from '@/components/business/crm-operation-button/index.vue';
  import CrmSelectPoolModal from '@/components/business/crm-select-pool-modal/index.vue';
  import CrmTableExportModal from '@/components/business/crm-table-export-modal/index.vue';
  import CrmTransferCustomerModal from '@/components/business/crm-transfer-customer-modal/index.vue';
  import CrmViewSelect from '@/components/business/crm-view-select/index.vue';
  import customerOverviewDrawer from './customerOverviewDrawer.vue';
  import customerSmsModal from './customerSmsModal.vue';

  import {
    batchDeleteCustomerByCondition,
    batchTransferCustomer,
    batchUpdateAccount,
    deleteCustomer,
    getCustomerNextStage,
    getCustomerStageConfig,
  } from '@/api/modules';
  import { baseFilterConfigList } from '@/config/clue';
  import useCustomerListColumns from '@/hooks/useCustomerListColumns';
  import {
    formatCustomerStageName,
    getCustomerLevelStarCount,
    setCustomerLevelOnRow,
    setInputMultipleTagsOnRow,
  } from '@/hooks/useCustomerListDescription';
  import useCustomerListWindow, {
    adjustVirtualListScrollAfterTrim,
    CUSTOMER_LIST_PAGE_SIZE,
  } from '@/hooks/useCustomerListWindow';
  import useFormCreateApi from '@/hooks/useFormCreateApi';
  import useFormCreateTable from '@/hooks/useFormCreateTable';
  import useListScrollLightweight from '@/hooks/useListScrollLightweight';
  import useModal from '@/hooks/useModal';
  import useViewChartParams, { STORAGE_VIEW_CHART_KEY, ViewChartResult } from '@/hooks/useViewChartParams';
  import useViewStore from '@/store/modules/view';
  import { appendCustomerReachStatusExportFields, getExportColumns } from '@/utils/export';
  import { hasAnyPermission } from '@/utils/permission';

  import { CustomerRouteEnum } from '@/enums/routeEnum';

  import { useCustomerReach } from '../hooks/useCustomerReach';
  import type { InternalRowData } from 'naive-ui/es/data-table/src/interface';

  const Message = useMessage();
  const { openModal } = useModal();
  const { t } = useI18n();
  const route = useRoute();
  const { currentLocale } = useLocale(Message.loading);
  const dialCardSlotOptions = [
    { label: '卡槽1', key: 1 },
    { label: '卡槽2', key: 2 },
  ];
  const props = defineProps<{
    formKey: FormDesignKeyEnum.CUSTOMER | FormDesignKeyEnum.SEARCH_ADVANCED_CUSTOMER;
    hiddenAdvanceFilter?: boolean;
    readonly?: boolean;
    isLimitShowDetail?: boolean; // 是否根据权限限查看详情
    hiddenTotal?: boolean;
  }>();

  const useListLayout = computed(() => props.formKey === FormDesignKeyEnum.CUSTOMER && !props.readonly);
  /** 虚拟列表行高，须与 .customer-list-item-wrap 高度一致 */
  const listItemHeight = 96;
  const listInfoColumnWidth = 280;
  const listOperationColumnWidth = 120;

  const emit = defineEmits<{
    (e: 'init', val: { filterConfigList: FilterFormItem[]; customFieldsFilterConfig: FilterFormItem[] }): void;
    (e: 'showCountDetail', row: Record<string, any>, type: 'opportunity' | 'clue'): void;
  }>();

  const activeTab = ref();

  const showListCheckbox = computed(
    () => useListLayout.value && activeTab.value !== CustomerSearchTypeEnum.CUSTOMER_COLLABORATION
  );

  const checkedRowKeys = ref<DataTableRowKey[]>([]);
  const checkedIdSet = computed(() => new Set(checkedRowKeys.value));
  const crmListRef = ref<InstanceType<typeof CrmList> | null>(null);
  const listLoadingMode = ref<'full' | 'more'>('full');
  const listLoadKind = ref<'replace' | 'append'>('replace');
  const listIdsBeforeLoad = ref<Set<string>>(new Set());
  const keyword = ref('');
  const formCreateDrawerVisible = ref(false);
  const activeSourceId = ref('');
  const initialSourceName = ref('');
  const needInitDetail = ref(false);
  const activeFormKey = ref(FormDesignKeyEnum.CUSTOMER);
  const otherFollowRecordSaveParams = ref({
    type: 'CUSTOMER',
    customerId: '',
    id: '',
  });

  const planFormDrawerVisible = ref(false);
  const planFormSaveParams = ref<Record<string, any>>({ converted: false });

  const stageConfig = ref<Awaited<ReturnType<typeof getCustomerStageConfig>>>();
  const excludeStageIds = computed<string[]>(() => {
    if (!stageConfig.value?.stageConfigList) return [];
    return stageConfig.value.stageConfigList
      .filter((s) => s.type === 'END' && (s.name?.includes('回款') || s.name?.includes('无效')))
      .map((s) => s.id);
  });
  async function initStageConfig() {
    try {
      stageConfig.value = await getCustomerStageConfig();
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log('initStageConfig error:', error);
    }
  }

  function handleNewClick() {
    needInitDetail.value = false;
    activeFormKey.value = FormDesignKeyEnum.CUSTOMER;
    activeSourceId.value = '';
    formCreateDrawerVisible.value = true;
  }

  const selectedRows = ref<InternalRowData[]>([]);

  const actionConfig = computed<BatchActionConfig>(() => ({
    baseAction: [
      {
        label: t('common.exportChecked'),
        key: 'exportChecked',
        permission: ['CUSTOMER_MANAGEMENT:EXPORT'],
      },
    ],
  }));

  const tableRefreshId = ref(0);
  const tableRemoveRefreshId = ref('');

  function handleDeleteByCondition() {
    // eslint-disable-next-line no-use-before-define
    const total = propsRes.value.crmPagination?.itemCount || 0;
    if (!total) {
      Message.warning(t('customer.batchDeleteByConditionEmptyTip'));
      return;
    }
    openModal({
      type: 'error',
      title: t('customer.batchDeleteByConditionTitleTip', { number: total }),
      content: t('customer.batchDeleteContentTip'),
      positiveText: t('common.confirmDelete'),
      negativeText: t('common.cancel'),
      onPositiveClick: async () => {
        try {
          const data = await batchDeleteCustomerByCondition({
            // eslint-disable-next-line no-use-before-define
            ...tableQueryParams.value,
            viewId: activeTab.value as CustomerSearchTypeEnum,
          });
          if (!data?.accepted) {
            Message.warning(data?.message || t('common.operationFailed'));
            return;
          }
          checkedRowKeys.value = [];
          Message.success(data.message || t('customer.batchDeleteTaskSubmitted'));
        } catch (error) {
          // eslint-disable-next-line no-console
          console.error(error);
        }
      },
    });
  }

  const showMoveModal = ref(false);
  const moveIds = ref<(string | number) | (string | number)[]>('');
  const showSelectPoolModal = ref(false);
  const showToPoolByConditionModal = ref(false);
  const showEditByConditionModal = ref(false);
  const customerListRenderKey = ref(0);
  const selectedPoolId = ref<string>('');

  function handleMoveToOpenSea(row?: any) {
    initialSourceName.value = row?.name ?? '';
    moveIds.value = row?.id ? row.id : checkedRowKeys.value;
    showSelectPoolModal.value = true;
  }

  function handlePoolSelected(poolId: string) {
    selectedPoolId.value = poolId;
    showSelectPoolModal.value = false;
    showMoveModal.value = true;
  }

  function handleRowKeyChange(keys: DataTableRowKey[], _rows: InternalRowData[]) {
    selectedRows.value = _rows;
  }

  const showTransferByConditionModal = ref(false);
  const showExportModal = ref<boolean>(false);

  const isExportAll = ref(false);
  function handleBatchAction(item: ActionsItem) {
    switch (item.key) {
      case 'exportChecked':
        isExportAll.value = false;
        showExportModal.value = true;
        break;
      default:
        break;
    }
  }

  function handleExportCreateSuccess() {
    checkedRowKeys.value = [];
  }

  // 删除
  function handleDelete(row: any) {
    openModal({
      type: 'error',
      title: t('common.deleteConfirmTitle', { name: characterLimit(row.name) }),
      content: t('customer.batchDeleteContentTip'),
      positiveText: t('common.confirmDelete'),
      negativeText: t('common.cancel'),
      onPositiveClick: async () => {
        try {
          await deleteCustomer(row.id);
          Message.success(t('common.deleteSuccess'));
          tableRemoveRefreshId.value = row.id;
        } catch (error) {
          // eslint-disable-next-line no-console
          console.error(error);
        }
      },
    });
  }

  // 单个转移弹窗
  const showSingleTransferModal = ref(false);

  async function handleSingleTransfer(row: any) {
    activeSourceId.value = row.id;
    checkedRowKeys.value = [row.id];
    showSingleTransferModal.value = true;
  }

  const {
    fieldList: customerFormFields,
    linkFormFieldMap,
    initFormConfig,
    initFormDetail,
  } = useFormCreateApi({
    formKey: computed(() => FormDesignKeyEnum.CUSTOMER),
    sourceId: activeSourceId,
    needInitDetail: computed(() => true),
  });

  async function handleActionSelect(row: any, actionKey: string) {
    switch (actionKey) {
      case 'edit':
        activeFormKey.value = FormDesignKeyEnum.CUSTOMER;
        activeSourceId.value = row.id;
        needInitDetail.value = true;
        otherFollowRecordSaveParams.value.id = row.id;
        linkFormFieldMap.value = {};
        formCreateDrawerVisible.value = true;
        break;
      case 'followUp':
        activeFormKey.value = FormDesignKeyEnum.FOLLOW_RECORD_CUSTOMER;
        activeSourceId.value = row.id;
        needInitDetail.value = false;
        initialSourceName.value = row.name;
        otherFollowRecordSaveParams.value.customerId = row.id;
        if (customerFormFields.value.length === 0) {
          await initFormConfig();
        }
        await initFormDetail(false, true);
        formCreateDrawerVisible.value = true;
        break;
      case 'transfer':
        handleSingleTransfer(row);
        break;
      case 'delete':
        handleDelete(row);
        break;
      case 'moveToOpenSea':
        handleMoveToOpenSea(row);
        break;
      default:
        break;
    }
  }

  const operationGroupList = computed<ActionsItem[]>(() => {
    return [
      {
        label: t('opportunity.followUp'),
        key: 'followUp',
        permission: ['CUSTOMER_MANAGEMENT:UPDATE'],
      },
      ...(activeTab.value !== CustomerSearchTypeEnum.CUSTOMER_COLLABORATION
        ? [
            {
              label: t('common.edit'),
              key: 'edit',
              permission: ['CUSTOMER_MANAGEMENT:UPDATE'],
            },
            {
              label: t('common.transfer'),
              key: 'transfer',
              permission: ['CUSTOMER_MANAGEMENT:TRANSFER'],
            },
            {
              label: 'more',
              key: 'more',
              slotName: 'more',
            },
          ]
        : []),
    ];
  });

  // 概览
  const showOverviewDrawer = ref(false);
  const crmTableRef = ref<InstanceType<typeof CrmTable>>();
  const handleAdvanceFilter = ref<null | ((...args: any[]) => void)>(null);
  const handleSearchData = ref<null | ((...args: any[]) => void)>(null);

  defineExpose({
    handleAdvanceFilter,
    handleSearchData,
  });

  function readCallStatus(row: any) {
    if (Number.isInteger(row.callStatus)) {
      return row.callStatus;
    }
    return 0;
  }

  function getCallStatusText(row: any) {
    const status = readCallStatus(row);
    if (status === -1) {
      return t('customer.callStatusInitiated');
    }
    if (status === 1) {
      return t('customer.callStatusNotConnected');
    }
    if (status === 2) {
      return t('customer.callStatusConnected');
    }
    return t('customer.callStatusNotDialed');
  }

  function readWechatFriendStatus(row: any) {
    if (Number.isInteger(row.wechatFriendStatus)) {
      return row.wechatFriendStatus;
    }
    return 0;
  }

  const {
    showReachModal,
    reachModal,
    activeWechatOptions,
    isReachOwner,
    handleDialCustomer,
    handleSmsCustomer,
    handleWechatCustomer,
    handleWechatFriendCustomer,
    handleReachModalSubmit,
  } = useCustomerReach({
    onStatusUpdated: () => {
      tableRefreshId.value += 1;
    },
  });

  const {
    navigationBuffer,
    enterRowIds,
    resetNavigationBuffer,
    mergeNavigationBuffer,
    markEnterBatch,
    clearEnterRowId,
    trimDisplayWindow,
    removeFromNavigationBuffer,
  } = useCustomerListWindow(readCallStatus, readWechatFriendStatus);

  const { isListScrolling, markListScrolling } = useListScrollLightweight();

  function isListRowEnter(id: string) {
    return enterRowIds.value.has(String(id));
  }

  function getWxFriendStatusText(row: any) {
    const status = readWechatFriendStatus(row);
    if (status === -1) {
      return t('customer.wechatFriendInitiated');
    }
    if (status === 2) {
      return t('customer.wechatFriendAdded');
    }
    if (status === 1) {
      return t('customer.wechatFriendPending');
    }
    return t('customer.wechatFriendNotAdded');
  }

  await initStageConfig();
  const { useTableRes, customFieldsFilterConfig, fieldList } = await useFormCreateTable({
    formKey: props.formKey,
    excludeFieldIds: ['callStatus', 'wechatFriendStatus'],
    disabledSelection: (row: any) => {
      return row.collaborationType === 'READ_ONLY';
    },
    operationColumn: props.readonly
      ? undefined
      : {
          key: 'operation',
          width: currentLocale.value === 'en-US' ? 250 : 200,
          fixed: 'right',
          render: (row: any) =>
            ['convertedToCustomer', 'convertedToOpportunity'].includes(activeTab.value) ||
            row.collaborationType === 'READ_ONLY'
              ? '-'
              : h(CrmOperationButton, {
                  groupList: operationGroupList.value.filter(
                    (item) => item.key !== 'followUp' || !excludeStageIds.value.includes(row.stage)
                  ),
                  moreList: [
                    ...(activeTab.value !== CustomerSearchTypeEnum.CUSTOMER_COLLABORATION
                      ? [
                          ...(['MANUAL_CREATE', 'PRIVATE_IMPORT'].includes(row.createSource)
                            ? []
                            : [
                                {
                                  label: t('customer.moveToOpenSea'),
                                  key: 'moveToOpenSea',
                                  permission: ['CUSTOMER_MANAGEMENT:RECYCLE'],
                                },
                              ]),
                          {
                            label: t('common.delete'),
                            key: 'delete',
                            danger: true,
                            permission: ['CUSTOMER_MANAGEMENT:DELETE'],
                          },
                        ]
                      : []),
                  ],
                  onSelect: (key: string) => handleActionSelect(row, key),
                }),
        },
    specialRender: {
      name: (row: any) => {
        return props.isLimitShowDetail && row.hasPermission === false
          ? h(CrmNameTooltip, { text: row.name })
          : h(
              CrmTableButton,
              {
                onClick: () => {
                  activeFormKey.value = FormDesignKeyEnum.CUSTOMER;
                  activeSourceId.value = row.id;
                  showOverviewDrawer.value = true;
                },
              },
              { trigger: () => row.name, default: () => row.name }
            );
      },
      opportunityCount: (row: any) => {
        return !row.opportunityCount
          ? row.opportunityCount ?? '-'
          : h(
              NButton,
              {
                text: true,
                type: 'primary',
                disabled: !row.opportunityModuleEnable || !hasAnyPermission(['OPPORTUNITY_MANAGEMENT:READ']),
                onClick: () => {
                  emit('showCountDetail', row, 'opportunity');
                },
              },
              { default: () => row.opportunityCount }
            );
      },
      clueCount: (row: any) => {
        return !row.clueCount
          ? row.clueCount ?? '-'
          : h(
              NButton,
              {
                text: true,
                type: 'primary',
                disabled:
                  !row.clueModuleEnable || !hasAnyPermission(['CLUE_MANAGEMENT:READ', 'CLUE_MANAGEMENT_POOL:READ']),
                onClick: () => {
                  emit('showCountDetail', row, 'clue');
                },
              },
              { default: () => row.clueCount }
            );
      },
      stage: (row: any) => formatCustomerStageName(row) || '-',
    },
    permission: [
      'CUSTOMER_MANAGEMENT:RECYCLE',
      'CUSTOMER_MANAGEMENT:UPDATE',
      'CUSTOMER_MANAGEMENT:DELETE',
      'CUSTOMER_MANAGEMENT:TRANSFER',
    ],
    // 列表模式使用虚拟滚动，勿设置 containerClass，避免 useTable 按 .v-vl 高度自动连页请求
    containerClass: props.formKey === FormDesignKeyEnum.CUSTOMER && !props.readonly ? '' : '.crm-customer-table',
    hiddenTotal: ref(!!props.hiddenTotal),
    readonly: props.readonly,
    customerStage: stageConfig.value?.stageConfigList || [],
  });
  const { propsRes, propsEvent, tableQueryParams, loadList, setLoadListParams, setAdvanceFilter } = useTableRes;

  function snapshotListIdsBeforeLoad() {
    listIdsBeforeLoad.value = new Set(propsRes.value.data.map((row) => String(row.id)));
  }

  function applyCustomerListAfterLoad() {
    if (!useListLayout.value) return;
    const data = propsRes.value.data as Record<string, any>[];
    const pageSize = propsRes.value.crmPagination?.pageSize ?? CUSTOMER_LIST_PAGE_SIZE;

    if (listLoadKind.value === 'replace') {
      resetNavigationBuffer();
      mergeNavigationBuffer(data);
      if (data.length) {
        markEnterBatch(data.map((row) => String(row.id)));
      }
    } else {
      const newRows = data.filter((row) => !listIdsBeforeLoad.value.has(String(row.id)));
      mergeNavigationBuffer(newRows);
      if (newRows.length) {
        markEnterBatch(newRows.map((row) => String(row.id)));
      }
    }

    const { removedCount } = trimDisplayWindow(data, pageSize);
    if (removedCount > 0) {
      nextTick(() => {
        adjustVirtualListScrollAfterTrim(crmListRef, removedCount, listItemHeight);
      });
    }
  }

  function beginListLoad(kind: 'replace' | 'append') {
    if (!useListLayout.value) return;
    listLoadKind.value = kind;
    if (kind === 'append') {
      snapshotListIdsBeforeLoad();
      listLoadingMode.value = 'more';
    } else {
      listLoadingMode.value = propsRes.value.data.length > 0 ? 'more' : 'full';
    }
  }

  const overviewNavigationRows = computed(() => {
    if (props.formKey !== FormDesignKeyEnum.CUSTOMER || !useListLayout.value) {
      return undefined;
    }
    return navigationBuffer.value;
  });

  const navigationTotal = computed(() => propsRes.value.crmPagination?.itemCount ?? 0);

  const navigationHasMore = computed(() => {
    const pagination = propsRes.value.crmPagination;
    if (!pagination?.page || !pagination.pageSize || pagination.itemCount == null) {
      return false;
    }
    return pagination.page * pagination.pageSize < pagination.itemCount;
  });

  async function handleNavigationLoadMore() {
    const pagination = propsRes.value.crmPagination;
    if (!pagination?.page || propsRes.value.loading) {
      return;
    }
    beginListLoad('append');
    await propsEvent.value.pageChange(pagination.page + 1);
  }

  watch(
    () => propsRes.value.loading,
    (loading, wasLoading) => {
      if (!useListLayout.value) return;
      if (loading && !wasLoading) {
        const page = propsRes.value.crmPagination?.page ?? 1;
        if (page <= 1) {
          listLoadKind.value = 'replace';
          listLoadingMode.value = propsRes.value.data.length > 0 ? 'more' : 'full';
        } else if (listLoadKind.value !== 'append') {
          listLoadKind.value = 'append';
          snapshotListIdsBeforeLoad();
          listLoadingMode.value = 'more';
        }
      }
      if (!loading && wasLoading) {
        applyCustomerListAfterLoad();
        listLoadingMode.value = 'full';
      }
    }
  );

  const customerLevelFieldId = computed(
    () => fieldList.value.find((field) => field.internalKey === 'customerLevel')?.id
  );

  const updatingCustomerLevelId = ref<string | null>(null);

  const editableTagFieldIds = computed(
    () =>
      new Set(
        fieldList.value
          .filter(
            (field) => field.type === FieldTypeEnum.INPUT_MULTIPLE && field.editable !== false && !field.resourceFieldId
          )
          .map((field) => field.id)
      )
  );

  const canEditListTags = computed(
    () =>
      hasAnyPermission(['CUSTOMER_MANAGEMENT:UPDATE']) &&
      activeTab.value !== CustomerSearchTypeEnum.CUSTOMER_COLLABORATION
  );

  const updatingCustomerTag = ref<{ rowId: string; fieldId: string } | null>(null);

  function normalizeTagList(tags: string[]) {
    return tags.map((tag) => String(tag).trim()).filter(Boolean);
  }

  function isSameTagList(current: string[], next: string[]) {
    const a = normalizeTagList(current);
    const b = normalizeTagList(next);
    if (a.length !== b.length) return false;
    return a.every((tag, index) => tag === b[index]);
  }

  function getRowTags(row: Record<string, any>, fieldId: string): string[] {
    const moduleField = row.moduleFields?.find(
      (field: { fieldId?: string; fieldValue?: unknown }) => field.fieldId === fieldId
    );
    const raw = moduleField?.fieldValue ?? row[fieldId];
    if (!Array.isArray(raw)) return [];
    return raw.map((item) => String(item));
  }

  async function handleCustomerTagChange(row: Record<string, any>, fieldId: string, tags: string[]) {
    if (!fieldId || updatingCustomerTag.value || !canEditListTags.value || !editableTagFieldIds.value.has(fieldId)) {
      return;
    }
    const normalizedTags = normalizeTagList(tags);
    if (normalizedTags.length > 10) {
      Message.warning(t('crmFormCreate.basic.tagInputLimitTip'));
      return;
    }
    const currentTags = getRowTags(row, fieldId);
    if (isSameTagList(currentTags, normalizedTags)) {
      return;
    }
    updatingCustomerTag.value = { rowId: row.id, fieldId };
    try {
      await batchUpdateAccount({
        ids: [row.id],
        fieldId,
        fieldValue: normalizedTags,
      });
      setInputMultipleTagsOnRow(row, fieldId, normalizedTags);
      Message.success(t('common.updateSuccess'));
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
    } finally {
      updatingCustomerTag.value = null;
    }
  }

  async function handleCustomerLevelChange(row: Record<string, any>, level: number) {
    const fieldId = customerLevelFieldId.value;
    if (
      !fieldId ||
      updatingCustomerLevelId.value ||
      !hasAnyPermission(['CUSTOMER_MANAGEMENT:UPDATE']) ||
      activeTab.value === CustomerSearchTypeEnum.CUSTOMER_COLLABORATION
    ) {
      return;
    }
    const currentLevel = getCustomerLevelStarCount(row, fieldId);
    if (currentLevel === level) {
      return;
    }
    updatingCustomerLevelId.value = row.id;
    try {
      await batchUpdateAccount({
        ids: [row.id],
        fieldId,
        fieldValue: String(level),
      });
      setCustomerLevelOnRow(row, fieldId, level);
      Message.success(t('common.updateSuccess'));
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
    } finally {
      updatingCustomerLevelId.value = null;
    }
  }

  function getListColumnWidth(column: CrmDataTableColumn) {
    const w = column.width;
    if (typeof w === 'number') return Math.max(w, 80);
    return 120;
  }

  const dynamicScrollRef = ref<HTMLElement | null>(null);
  const dynamicScrollLeft = ref(0);
  const customerListScrollbarTrackRef = ref<HTMLElement | null>(null);
  const customerListScrollbarVisible = ref(false);
  const customerListScrollbarTop = ref(0);
  const customerListScrollbarHeight = ref(40);
  const customerListScrollbarDragging = ref(false);
  let customerListScrollbarStartY = 0;
  let customerListScrollbarStartTop = 0;

  const viewStore = useViewStore();

  const isCustomerListNoMoreData = computed(() => {
    const pagination = propsRes.value.crmPagination;
    if (!pagination?.page || !pagination.pageSize || pagination.itemCount == null) {
      return false;
    }
    return pagination.page * pagination.pageSize >= pagination.itemCount;
  });

  const showCustomerListScrollbar = computed(
    () => useListLayout.value && propsRes.value.data.length > 0 && customerListScrollbarVisible.value
  );

  const customerListScrollbarThumbStyle = computed(() => ({
    height: `${customerListScrollbarHeight.value}px`,
    transform: `translateY(${customerListScrollbarTop.value}px)`,
  }));

  const tableBindProps = computed(() => ({
    ...propsRes.value,
  }));

  function showListItemOperation(row: Record<string, any>) {
    return (
      !['convertedToCustomer', 'convertedToOpportunity'].includes(activeTab.value) &&
      row.collaborationType !== 'READ_ONLY'
    );
  }

  const listOperationMoreDeleteOnly = computed<ActionsItem[]>(() => [
    {
      label: t('common.delete'),
      key: 'delete',
      danger: true,
      permission: ['CUSTOMER_MANAGEMENT:DELETE'],
    },
  ]);

  const listOperationMoreWithPool = computed<ActionsItem[]>(() => [
    {
      label: t('customer.moveToOpenSea'),
      key: 'moveToOpenSea',
      permission: ['CUSTOMER_MANAGEMENT:RECYCLE'],
    },
    ...listOperationMoreDeleteOnly.value,
  ]);

  function resolveListOperationMoreList(row: Record<string, any>) {
    if (activeTab.value === CustomerSearchTypeEnum.CUSTOMER_COLLABORATION) {
      return [];
    }
    if (['MANUAL_CREATE', 'PRIVATE_IMPORT'].includes(row.createSource)) {
      return listOperationMoreDeleteOnly.value;
    }
    return listOperationMoreWithPool.value;
  }

  function handleOpenCustomerDetail(row: Record<string, any>) {
    if (props.isLimitShowDetail && row.hasPermission === false) {
      return;
    }
    activeFormKey.value = FormDesignKeyEnum.CUSTOMER;
    activeSourceId.value = row.id;
    showOverviewDrawer.value = true;
  }

  function isListRowSelectable(row: Record<string, any>) {
    return row.collaborationType !== 'READ_ONLY';
  }

  const listSelectableIds = computed(() =>
    propsRes.value.data.filter(isListRowSelectable).map((row) => row.id as DataTableRowKey)
  );

  const listHeaderCheckState = computed(() => {
    const selectableIds = listSelectableIds.value;
    const selectedSet = checkedIdSet.value;
    let selectedCount = 0;
    selectableIds.forEach((id) => {
      if (selectedSet.has(id)) selectedCount += 1;
    });
    return {
      checked: selectableIds.length > 0 && selectedCount === selectableIds.length,
      indeterminate: selectedCount > 0 && selectedCount < selectableIds.length,
    };
  });

  function syncListSelectedRows() {
    selectedRows.value = propsRes.value.data.filter((item) =>
      checkedRowKeys.value.includes(item.id)
    ) as InternalRowData[];
    handleRowKeyChange(checkedRowKeys.value, selectedRows.value);
  }

  function handleListSelectAll(checked: boolean) {
    const currentSelected = new Set(checkedRowKeys.value);
    listSelectableIds.value.forEach((id) => {
      if (checked) {
        currentSelected.add(id);
      } else {
        currentSelected.delete(id);
      }
    });
    checkedRowKeys.value = Array.from(currentSelected);
    syncListSelectedRows();
  }

  function handleListItemCheckChange(row: Record<string, any>, checked: boolean) {
    const id = row.id as DataTableRowKey;
    if (checked) {
      if (!checkedRowKeys.value.includes(id)) {
        checkedRowKeys.value = [...checkedRowKeys.value, id];
      }
    } else {
      checkedRowKeys.value = checkedRowKeys.value.filter((key) => key !== id);
    }
    syncListSelectedRows();
  }

  function handleListReachBottom() {
    const pagination = propsRes.value.crmPagination;
    if (!pagination?.page || !pagination.pageSize || !pagination.itemCount || propsRes.value.loading) {
      return;
    }
    if (pagination.page * pagination.pageSize >= pagination.itemCount) {
      return;
    }
    beginListLoad('append');
    propsEvent.value.pageChange(pagination.page + 1);
  }

  function getCustomerListScrollElement() {
    return ((crmListRef.value as any)?.$el?.querySelector('.v-vl') as HTMLElement | null) ?? null;
  }

  function syncCustomerListScrollbar() {
    if (!useListLayout.value) {
      customerListScrollbarVisible.value = false;
      return;
    }
    const scrollEl = getCustomerListScrollElement();
    const trackEl = customerListScrollbarTrackRef.value;
    if (!scrollEl || !trackEl) {
      customerListScrollbarVisible.value = false;
      return;
    }
    const scrollRange = scrollEl.scrollHeight - scrollEl.clientHeight;
    if (scrollRange <= 1) {
      customerListScrollbarVisible.value = false;
      return;
    }
    const trackHeight = trackEl.clientHeight;
    const thumbHeight = Math.max(40, Math.round((scrollEl.clientHeight / scrollEl.scrollHeight) * trackHeight));
    const thumbRange = Math.max(trackHeight - thumbHeight, 0);
    customerListScrollbarHeight.value = thumbHeight;
    customerListScrollbarTop.value = Math.round((scrollEl.scrollTop / scrollRange) * thumbRange);
    customerListScrollbarVisible.value = true;
  }

  function handleCustomerListVirtualScroll() {
    markListScrolling();
    syncCustomerListScrollbar();
  }

  function scrollCustomerListByThumbTop(nextTop: number) {
    const scrollEl = getCustomerListScrollElement();
    const trackEl = customerListScrollbarTrackRef.value;
    if (!scrollEl || !trackEl) return;

    const thumbRange = Math.max(trackEl.clientHeight - customerListScrollbarHeight.value, 0);
    const scrollRange = scrollEl.scrollHeight - scrollEl.clientHeight;
    const boundedTop = Math.min(Math.max(nextTop, 0), thumbRange);
    scrollEl.scrollTop = thumbRange > 0 ? (boundedTop / thumbRange) * scrollRange : 0;
    syncCustomerListScrollbar();
  }

  function handleCustomerListScrollbarPointerMove(event: PointerEvent) {
    if (!customerListScrollbarDragging.value) return;
    const deltaY = event.clientY - customerListScrollbarStartY;
    scrollCustomerListByThumbTop(customerListScrollbarStartTop + deltaY);
  }

  function handleCustomerListScrollbarPointerUp() {
    customerListScrollbarDragging.value = false;
    window.removeEventListener('pointermove', handleCustomerListScrollbarPointerMove);
    window.removeEventListener('pointerup', handleCustomerListScrollbarPointerUp);
  }

  function handleCustomerListScrollbarPointerDown(event: PointerEvent) {
    const trackEl = customerListScrollbarTrackRef.value;
    if (!trackEl) return;

    event.preventDefault();
    const clickTop = event.clientY - trackEl.getBoundingClientRect().top;
    const target = event.target as HTMLElement;
    if (!target.classList.contains('customer-list-vertical-scrollbar__thumb')) {
      scrollCustomerListByThumbTop(clickTop - customerListScrollbarHeight.value / 2);
    }
    customerListScrollbarDragging.value = true;
    customerListScrollbarStartY = event.clientY;
    customerListScrollbarStartTop = customerListScrollbarTop.value;
    window.addEventListener('pointermove', handleCustomerListScrollbarPointerMove);
    window.addEventListener('pointerup', handleCustomerListScrollbarPointerUp);
  }

  watch(
    () => [propsRes.value.data.length, propsRes.value.loading, useListLayout.value],
    () => {
      nextTick(() => {
        requestAnimationFrame(syncCustomerListScrollbar);
      });
    }
  );

  onBeforeUnmount(() => {
    handleCustomerListScrollbarPointerUp();
  });

  const transferByConditionQueryParams = computed(() => ({
    ...tableQueryParams.value,
    viewId: activeTab.value as CustomerSearchTypeEnum,
  }));

  function handleTransferByConditionClick() {
    const total = propsRes.value.crmPagination?.itemCount || 0;
    if (!total) {
      Message.warning(t('customer.batchDeleteByConditionEmptyTip'));
      return;
    }
    showTransferByConditionModal.value = true;
  }

  function handleTransferByConditionSuccess() {
    checkedRowKeys.value = [];
  }

  function handleToPoolByConditionSuccess() {
    checkedRowKeys.value = [];
  }

  function handleEditByConditionClick() {
    const total = propsRes.value.crmPagination?.itemCount || 0;
    if (!total) {
      Message.warning(t('customer.batchDeleteByConditionEmptyTip'));
      return;
    }
    showEditByConditionModal.value = true;
  }

  function handleEditByConditionSuccess() {
    checkedRowKeys.value = [];
  }

  function waitForListLoadIdle(timeoutMs = 15000): Promise<void> {
    return new Promise((resolve) => {
      const start = Date.now();
      const check = () => {
        if (!propsRes.value.loading || Date.now() - start >= timeoutMs) {
          resolve();
          return;
        }
        setTimeout(check, 50);
      };
      check();
    });
  }

  async function refreshCustomerListAfterBatchByCondition() {
    await waitForListLoadIdle();
    checkedRowKeys.value = [];
    if (useListLayout.value) {
      resetNavigationBuffer();
      customerListRenderKey.value += 1;
      beginListLoad('replace');
      propsRes.value.data = [];
    }
    setLoadListParams({ keyword: keyword.value, viewId: activeTab.value });
    await loadList(false);
    await waitForListLoadIdle();
    crmTableRef.value?.scrollTo({ top: 0 });
  }

  function onCustomerBatchByConditionDone(event: Event) {
    if (props.readonly || route.name !== CustomerRouteEnum.CUSTOMER_INDEX) {
      return;
    }
    const { detail } = event as CustomEvent<CustomerBatchByConditionSseDetail>;
    if (detail?.viewId && String(detail.viewId) !== String(activeTab.value)) {
      return;
    }
    refreshCustomerListAfterBatchByCondition().catch(() => undefined);
  }

  const advancedNumberFilterFields = new Set(['callStatus', 'wechatFriendStatus']);
  const customerAdvancedFilterConfig = computed<FilterFormItem[]>(() =>
    (customFieldsFilterConfig.value as FilterFormItem[]).map((item) =>
      item.dataIndex && advancedNumberFilterFields.has(item.dataIndex) ? { ...item, valueType: 'number' } : item
    )
  );

  function buildCallStatusColumn() {
    return {
      title: t('customer.callStatus'),
      key: 'customerCallStatusText',
      width: 120,
      align: 'center',
      showInTable: true,
      columnSelectorDisabled: true,
      render: (row: any) => getCallStatusText(row),
    } as any;
  }

  function buildWxFriendStatusColumn() {
    return {
      title: t('customer.wechatFriendStatus'),
      key: 'customerWechatFriendStatusText',
      width: 120,
      align: 'center',
      showInTable: true,
      columnSelectorDisabled: true,
      render: (row: any) => getWxFriendStatusText(row),
    } as any;
  }

  function buildReachActionButton(options: {
    title: string;
    iconType?: string;
    iconComponent?: any;
    color?: string;
    showPlusBadge?: boolean;
    disabled?: boolean;
    onClick: () => void;
  }) {
    return h(
      'button',
      {
        type: 'button',
        title: options.title,
        disabled: options.disabled,
        style: {
          width: '24px',
          height: '24px',
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          border: 'none',
          background: 'transparent',
          cursor: options.disabled ? 'not-allowed' : 'pointer',
          opacity: options.disabled ? 0.45 : 1,
          padding: '0',
          position: 'relative',
        },
        onClick: (event: MouseEvent) => {
          event.stopPropagation();
          if (options.disabled) {
            return;
          }
          options.onClick();
        },
      },
      [
        options.iconType
          ? h(CrmIcon, {
              type: options.iconType,
              size: 16,
              color: options.color || 'var(--primary-8)',
            })
          : h(
              NIcon,
              {
                size: 18,
                color: options.color || '#07c160',
              },
              {
                default: () => h(options.iconComponent),
              }
            ),
        options.showPlusBadge
          ? h(
              'span',
              {
                style: {
                  position: 'absolute',
                  right: '-2px',
                  bottom: '-2px',
                  minWidth: '10px',
                  height: '10px',
                  borderRadius: '999px',
                  background: 'var(--primary-8)',
                  color: '#fff',
                  fontSize: '9px',
                  lineHeight: '10px',
                  textAlign: 'center',
                  border: '1px solid #fff',
                },
              },
              '+'
            )
          : null,
      ]
    );
  }

  function buildReachColumn() {
    return {
      title: t('customer.reach'),
      key: 'customerReach',
      width: 132,
      align: 'center',
      showInTable: true,
      columnSelectorDisabled: true,
      render: (row: any) => {
        const canOperate = isReachOwner(row);
        const wxStatus = readWechatFriendStatus(row);
        return h(
          'div',
          {
            style: {
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '8px',
            },
          },
          [
            h(
              NDropdown,
              {
                trigger: 'click',
                options: dialCardSlotOptions,
                placement: 'bottom-start',
                disabled: !canOperate,
                onSelect: (key: string | number) => handleDialCustomer(row, Number(key)),
              },
              {
                default: () =>
                  buildReachActionButton({
                    title: t('customer.reach.call'),
                    iconType: 'iconicon_call',
                    disabled: !canOperate,
                    onClick: () => undefined,
                  }),
              }
            ),
            h(
              NDropdown,
              {
                trigger: 'click',
                options: dialCardSlotOptions,
                placement: 'bottom-start',
                disabled: !canOperate,
                onSelect: (key: string | number) => handleSmsCustomer(row, Number(key)),
              },
              {
                default: () =>
                  buildReachActionButton({
                    title: t('customer.reach.sms'),
                    iconType: 'iconicon_chat',
                    disabled: !canOperate,
                    onClick: () => undefined,
                  }),
              }
            ),
            wxStatus === 2
              ? buildReachActionButton({
                  title: t('customer.reach.wechat'),
                  iconComponent: LogoWechat,
                  disabled: !canOperate,
                  onClick: () => handleWechatCustomer(row),
                })
              : null,
            wxStatus !== 2
              ? buildReachActionButton({
                  title: t('customer.reach.addWechat'),
                  iconComponent: LogoWechat,
                  showPlusBadge: true,
                  disabled: !canOperate,
                  onClick: () => handleWechatFriendCustomer(row),
                })
              : null,
          ]
        );
      },
    } as any;
  }

  /** 非协作视图均展示「沟通」列（含新增自定义视图） */
  const showReachColumn = computed(
    () => !props.readonly && activeTab.value !== CustomerSearchTypeEnum.CUSTOMER_COLLABORATION
  );

  const showStatusColumns = computed(() => true);

  const tableColumns = computed(() => {
    const removedColumnKeys = new Set(['recyclePoolName', 'reasonId', 'reservedDays']);
    const baseColumns = propsRes.value.columns.filter(
      (item: any) =>
        !removedColumnKeys.has(String(item.key)) &&
        !['customerDial', 'customerReach', 'customerCallStatusText', 'customerWechatFriendStatusText'].includes(
          String(item.key)
        )
    );
    const nameIndex = baseColumns.findIndex((item: any) => item.key === 'name');
    const orderIndex = baseColumns.findIndex((item: any) => item.key === SpecialColumnEnum.ORDER);
    if (nameIndex >= 0 && orderIndex >= 0 && nameIndex !== orderIndex + 1) {
      const [nameColumn] = baseColumns.splice(nameIndex, 1);
      baseColumns.splice(orderIndex + 1, 0, nameColumn);
    }

    let insertIndex = Math.min(baseColumns.length, 1);
    if (nameIndex >= 0) {
      insertIndex = nameIndex;
    } else if (orderIndex >= 0) {
      insertIndex = orderIndex + 1;
    }

    if (showReachColumn.value) {
      const dialColumn = buildReachColumn();
      baseColumns.splice(insertIndex, 0, dialColumn);
      insertIndex += 1;
    }

    if (showStatusColumns.value) {
      const callStatusColumn = buildCallStatusColumn();
      const wxFriendStatusColumn = buildWxFriendStatusColumn();
      baseColumns.splice(insertIndex, 0, callStatusColumn, wxFriendStatusColumn);
    }

    if (activeTab.value === CustomerSearchTypeEnum.CUSTOMER_COLLABORATION) {
      return baseColumns
        .filter((item: any) => item.type !== 'selection')
        .map((e) => {
          if (e.key === 'operation') {
            return { ...e, width: 80 };
          }
          return e;
        });
    }
    return baseColumns;
  });

  const { listMiddleColumns, refreshListMiddleColumns } = useCustomerListColumns(tableColumns, {
    enabled: useListLayout,
  });

  const listMiddleTotalWidth = computed(() =>
    listMiddleColumns.value.reduce((sum, column) => sum + getListColumnWidth(column), 0)
  );

  const dynamicMiddleTrackStyle = computed(() => ({
    width: `${listMiddleTotalWidth.value}px`,
  }));

  function handleDynamicScroll() {
    dynamicScrollLeft.value = dynamicScrollRef.value?.scrollLeft ?? 0;
  }

  function getHorizontalWheelDelta(event: WheelEvent) {
    if (event.deltaX !== 0) {
      return event.deltaX;
    }
    if (event.shiftKey) {
      return event.deltaY;
    }
    return 0;
  }

  function handleDynamicWheel(event: WheelEvent) {
    const el = dynamicScrollRef.value;
    if (!el || listMiddleTotalWidth.value <= el.clientWidth) return;
    const horizontalDelta = getHorizontalWheelDelta(event);
    if (!horizontalDelta) return;
    event.preventDefault();
    el.scrollLeft += horizontalDelta;
    dynamicScrollLeft.value = el.scrollLeft;
  }

  watch(listMiddleColumns, () => {
    dynamicScrollLeft.value = 0;
    if (dynamicScrollRef.value) {
      dynamicScrollRef.value.scrollLeft = 0;
    }
  });

  function getListColumnTitle(column: CrmDataTableColumn) {
    const { title } = column;
    if (typeof title === 'string') return title;
    return String(column.key ?? '');
  }

  function syncListColumnSortOrder(columnKey: string, order: 'ascend' | 'descend' | false) {
    const apply = (cols: CrmDataTableColumn[]) => {
      cols.forEach((col) => {
        if (!col.sorter && col.sortOrder === undefined) return;
        col.sortOrder = col.key === columnKey ? order : false;
      });
    };
    apply(listMiddleColumns.value);
    apply(propsRes.value.columns as CrmDataTableColumn[]);
    apply(tableColumns.value);
  }

  function handleListColumnSort(column: CrmDataTableColumn) {
    if (!column.sorter || column.key == null) return;
    const columnKey = String(column.key);
    const current = column.sortOrder ?? false;
    let next: 'ascend' | 'descend' | false = false;
    if (current === false) {
      next = 'ascend';
    } else if (current === 'ascend') {
      next = 'descend';
    }
    syncListColumnSortOrder(columnKey, next);
    let sortType = '';
    if (next === 'ascend') {
      sortType = 'asc';
    } else if (next === 'descend') {
      sortType = 'desc';
    }
    propsEvent.value.sorterChange(!next ? {} : { name: columnKey, type: sortType });
  }

  async function applyListColumnSortFromView(viewId: string) {
    await refreshListMiddleColumns();
    const sortObj = await viewStore.getViewSort(TableKeyEnum.CUSTOMER, viewId);
    if (sortObj && Object.keys(sortObj).length) {
      const order = sortObj.type === 'desc' ? 'descend' : 'ascend';
      syncListColumnSortOrder(sortObj.name as string, order);
      await propsEvent.value.sorterChange(sortObj);
      return;
    }
    syncListColumnSortOrder('', false);
    await propsEvent.value.sorterChange({});
  }

  function handleListColumnsSettingChange() {
    if (useListLayout.value) {
      refreshListMiddleColumns();
    }
  }

  const exportParams = computed(() => {
    return {
      ...tableQueryParams.value,
      ids: checkedRowKeys.value,
    };
  });

  function handleExportAllClick() {
    isExportAll.value = true;
    showExportModal.value = true;
  }

  const filterConfigList = computed<FilterFormItem[]>(() => [
    {
      title: t('opportunity.department'),
      dataIndex: 'departmentId',
      type: FieldTypeEnum.TREE_SELECT,
      treeSelectProps: {
        labelField: 'name',
        keyField: 'id',
        multiple: true,
        clearFilterAfterSelect: false,
        type: 'department',
        checkable: true,
        showContainChildModule: true,
        containChildIds: [],
      },
    },
    {
      title: t('customer.stage'),
      dataIndex: 'stage',
      type: FieldTypeEnum.SELECT_MULTIPLE,
      selectProps: {
        options:
          stageConfig.value?.stageConfigList.map((e) => ({
            label: e.name,
            value: e.id,
          })) || [],
      },
    },
    {
      title: t('customer.lastFollowUps'),
      dataIndex: 'follower',
      type: FieldTypeEnum.USER_SELECT,
    },
    {
      title: t('customer.lastFollowUpDate'),
      dataIndex: 'followTime',
      type: FieldTypeEnum.TIME_RANGE_PICKER,
    },
    {
      title: t('customer.collectionTime'),
      dataIndex: 'collectionTime',
      type: FieldTypeEnum.TIME_RANGE_PICKER,
    },
    {
      title: t('customer.followRecordContent'),
      dataIndex: 'followRecordContent',
      type: FieldTypeEnum.TEXTAREA,
    },
    ...baseFilterConfigList,
  ]);

  const exportColumns = computed<ExportTableColumnItem[]>(() =>
    appendCustomerReachStatusExportFields(
      getExportColumns(propsRes.value.columns, customFieldsFilterConfig.value as FilterFormItem[]),
      (key) => {
        const field = fieldList.value?.find((item) => item.businessKey === key);
        return field?.name ?? t(key === 'callStatus' ? 'customer.callStatus' : 'customer.wechatFriendStatus');
      }
    )
  );

  const isAdvancedSearchMode = ref(false);
  const advancedOriginalForm = ref<FilterForm | undefined>();
  function handleAdvSearch(filter: FilterResult, isAdvancedMode: boolean, originalForm?: FilterForm) {
    keyword.value = '';
    advancedOriginalForm.value = originalForm;
    isAdvancedSearchMode.value = isAdvancedMode;
    setAdvanceFilter(filter);
    if (useListLayout.value) {
      beginListLoad('replace');
    }
    loadList();
    crmTableRef.value?.scrollTo({ top: 0 });
  }

  handleAdvanceFilter.value = handleAdvSearch;

  const tableAdvanceFilterRef = ref<InstanceType<typeof CrmAdvanceFilter>>();

  function searchData(val?: string, refreshId?: string) {
    if (useListLayout.value && refreshId === undefined) {
      beginListLoad('replace');
    }
    setLoadListParams({ keyword: val ?? keyword.value, viewId: activeTab.value });
    loadList(false, refreshId);
    if (!refreshId) {
      crmTableRef.value?.scrollTo({ top: 0 });
    }
  }
  handleSearchData.value = searchData;

  async function openPlanForm(customerId: string, opportunityId?: string, contactId?: string) {
    let nextStageId = '';
    let nextStageName = '';
    let owner = '';
    let ownerName = '';
    let customerName = '';

    try {
      const res = await getCustomerNextStage(customerId);
      if (res) {
        nextStageId = res.nextStageId;
        nextStageName = res.nextStageName;
        owner = res.owner || '';
        ownerName = res.ownerName || '';
        customerName = res.customerName || '';
      }
    } catch (error) {
      // eslint-disable-next-line no-console
      console.error('获取客户下一阶段信息失败', error);
    }

    planFormSaveParams.value = {
      converted: false,
      customerId,
      customerName,
      opportunityId: opportunityId || '',
      contactId: contactId || '',
      type: 'CUSTOMER',
      nextStage: nextStageId,
      _nextStage: nextStageId,
      nextStageName,
      owner,
      ownerName,
    };
    planFormDrawerVisible.value = true;
  }

  function handlePlanSaved() {
    planFormSaveParams.value = { converted: false };
    searchData();
  }

  function handleFormCreateSaved(res: any) {
    if (needInitDetail.value) {
      searchData(undefined, res.id);
    } else {
      searchData();
    }
    formCreateDrawerVisible.value = false;

    if (res?.followResult === 'COMPLETED' && res?.customerId) {
      openPlanForm(res.customerId, res.opportunityId, res.contactId);
    }
  }

  function handleGeneratedChart(res: FilterResult, form: FilterForm) {
    advancedOriginalForm.value = form;
    setAdvanceFilter(res);
    tableAdvanceFilterRef.value?.setAdvancedFilter(res, true);
    searchData();
  }

  const { initTableViewChartParams, getChartViewId } = useViewChartParams();

  function viewChartCallBack(params: ViewChartResult) {
    const { viewId, formModel, filterResult } = params;
    tableAdvanceFilterRef.value?.initFormModal(formModel, true);
    setAdvanceFilter(filterResult);
    activeTab.value = viewId;
  }

  watch(
    () => [propsRes.value.columns, tableColumns.value],
    () => {
      if (useListLayout.value) {
        refreshListMiddleColumns();
      }
    },
    { deep: true }
  );

  watch(
    () => activeTab.value,
    (val) => {
      if (val) {
        showOverviewDrawer.value = false;
        if (useListLayout.value) {
          resetNavigationBuffer();
        }
        checkedRowKeys.value = [];
        setLoadListParams({ keyword: keyword.value, viewId: getChartViewId() ?? activeTab.value });
        initTableViewChartParams(viewChartCallBack);
        if (useListLayout.value) {
          setTimeout(() => {
            applyListColumnSortFromView(val);
          }, 300);
        } else {
          crmTableRef.value?.setColumnSort(val);
        }
      }
    },
    { immediate: true }
  );

  watch(
    () => tableRefreshId.value,
    () => {
      checkedRowKeys.value = [];
      searchData();
    }
  );

  function removeItemFromList(id: string) {
    propsRes.value.data = propsRes.value.data.filter((item) => item.id !== id);
    removeFromNavigationBuffer(id);
    propsRes.value.crmPagination = {
      ...propsRes.value.crmPagination,
      itemCount: (propsRes.value.crmPagination?.itemCount ?? 1) - 1,
    };
  }

  function handleMoveToOpenSeaByConditionClick() {
    const total = propsRes.value.crmPagination?.itemCount || 0;
    if (!total) {
      Message.warning(t('customer.batchDeleteByConditionEmptyTip'));
      return;
    }
     showToPoolByConditionModal.value = true;
  }

  function handleMoveRefresh() {
    if (Array.isArray(moveIds.value)) {
      checkedRowKeys.value = [];
      tableRefreshId.value += 1;
      return;
    }
    removeItemFromList(moveIds.value.toString());
  }

  watch(
    () => tableRemoveRefreshId.value,
    (val) => {
      if (val) {
        removeItemFromList(val);
      }
    }
  );

  onMounted(() => {
    emit('init', {
      filterConfigList: filterConfigList.value,
      customFieldsFilterConfig: customerAdvancedFilterConfig.value,
    });
    if (route.query.id) {
      activeFormKey.value = FormDesignKeyEnum.CUSTOMER;
      activeSourceId.value = route.query.id as string;
      showOverviewDrawer.value = true;
    }
    window.addEventListener(CUSTOMER_BATCH_BY_CONDITION_DOM_EVENT, onCustomerBatchByConditionDone);
  });

  onBeforeUnmount(() => {
    window.removeEventListener(CUSTOMER_BATCH_BY_CONDITION_DOM_EVENT, onCustomerBatchByConditionDone);
    sessionStorage.removeItem(STORAGE_VIEW_CHART_KEY);
  });
</script>

<style lang="less" scoped>
  :deep(.n-tabs-scroll-padding) {
    width: 16px !important;
  }
  .customer-list-panel {
    position: relative;
    flex: 1;
    min-height: 0;
    :deep(.n-spin) {
      height: 100%;
      min-height: 0;
    }
    :deep(.n-spin-content) {
      height: 100%;
      display: flex;
      flex-direction: column;
      min-height: 0;
    }
    .customer-list-table-header {
      border-radius: var(--border-radius-small) var(--border-radius-small) 0 0;
    }
    /* 表头（客户信息 / 操作）：与公海池 CrmTable / DataTable 主题一致 */
    .customer-list-table-header__info.customer-list-dynamic-th,
    .customer-list-table-header__operation.customer-list-dynamic-th {
      padding: 10px 16px;
      font-size: 14px;
      font-weight: 500;
      color: var(--text-n4);
      white-space: nowrap;
      background-color: var(--text-n10);
    }
    /* 动态列单元格：与公海池 n-data-table-td 文本一致 */
    :deep(.customer-list-dynamic-td) {
      display: flex;
      flex-direction: column;
      justify-content: center;
      height: 100%;
      padding: 0 16px;
      font-size: 14px;
      color: var(--text-n1);
      vertical-align: middle;
    }
    :deep(.customer-list-dynamic-td .crm-customer-list-cell),
    :deep(.customer-list-dynamic-td .customer-list-dynamic-cell),
    :deep(.customer-list-dynamic-td .n-ellipsis) {
      font-size: 14px;
      color: var(--text-n1);
    }
    .customer-list-dynamic-header-track {
      flex-shrink: 0;
      transform: translateX(calc(-1 * var(--customer-list-scroll-left, 0px)));
    }
    .customer-list-item-wrap--enter {
      animation: customerListRowIn 0.2s ease-out;
    }
    @keyframes customerListRowIn {
      from {
        opacity: 0;
        transform: translateY(4px);
      }
      to {
        opacity: 1;
        transform: translateY(0);
      }
    }
    .customer-list-dynamic-scrollbar-row {
      flex-shrink: 0;
      min-height: 14px;
      border: 1px solid var(--text-n8);
      border-top: none;
      background-color: var(--text-n10);
      border-radius: 0 0 var(--border-radius-small) var(--border-radius-small);
    }
    .customer-list-dynamic-scrollbar {
      height: 14px;
      min-height: 14px;
      overflow-x: auto;
      overflow-y: hidden;
      scrollbar-width: thin;
      &::-webkit-scrollbar {
        height: 8px;
      }
      &::-webkit-scrollbar-thumb {
        border-radius: 4px;
        background-color: var(--text-n6);
      }
      &::-webkit-scrollbar-track {
        background-color: transparent;
      }
    }
    .customer-list-dynamic-scrollbar__inner {
      height: 1px;
      pointer-events: none;
    }
    .customer-list-vertical-scrollbar {
      position: absolute;
      top: 43px;
      right: 3px;
      bottom: 15px;
      z-index: 4;
      width: 8px;
      opacity: 0;
      pointer-events: none;
      transition: opacity 0.12s ease;
    }
    .customer-list-vertical-scrollbar--visible {
      opacity: 1;
      pointer-events: auto;
    }
    .customer-list-vertical-scrollbar__thumb {
      width: 8px;
      min-height: 40px;
      border-radius: 4px;
      background-color: var(--text-n6);
      cursor: pointer;
      will-change: transform;
    }
    .customer-list-scroll {
      width: 100%;
      min-height: 0;
      border: 1px solid var(--text-n8);
      border-top: none;
      border-bottom: none;
      :deep(.crm-list),
      :deep(.n-virtual-list) {
        height: 100% !important;
        min-height: 0;
      }
      :deep(.v-vl) {
        overflow-y: auto !important;
        scrollbar-width: thin;
        scrollbar-color: var(--text-n6) transparent;
        &::-webkit-scrollbar {
          width: 8px;
        }
        &::-webkit-scrollbar-thumb {
          border-radius: 4px;
          background-color: var(--text-n6);
        }
        &::-webkit-scrollbar-track {
          background-color: transparent;
        }
      }
    }
    .customer-list-item-wrap {
      box-sizing: border-box;
      overflow: hidden;
      :deep(.crm-customer-list-item) {
        height: 100%;
      }
    }
  }
</style>
