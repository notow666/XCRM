<template>
  <div :class="`crm-follow-detail ${props.wrapperClass}`">
    <div class="p-[24px] pb-[16px] flex items-center justify-between">
      <div>
        <n-button v-if="showAdd" type="primary" @click="handleAdd">
          {{ t(props.activeType === 'followPlan' ? 'crmFollowRecord.writePlan' : 'crmFollowRecord.writeRecord') }}
        </n-button>
      </div>
      <div class="flex gap-[12px]">
        <CrmSearchInput
          v-model:value="followKeyword"
          :placeholder="t('common.byKeywordSearch')"
          class="mr-[1px] !w-[240px]"
          @search="(val) => searchData(val)"
        />
      </div>
    </div>
    <n-spin :show="loading" class="flex-1">
      <FollowRecord
        v-model:data="data"
        :virtual-scroll-height="'100%'"
        :get-description-fun="getDescriptionFun"
        key-field="id"
        :disabled-open-detail="props.followApiKey !== 'myPlan'"
        :type="props.activeType"
        :empty-text="emptyText"
        :get-disabled-fun="getShowAction"
        @reach-bottom="handleReachBottom"
        @change="changePlanStatus"
      >
        <template #headerAction="{ item }">
          <div class="flex items-center gap-[12px]">
            <n-button type="primary" class="text-btn-primary" quaternary @click="handleDetail(item)">
              {{ t('common.detail') }}
            </n-button>
            <n-button
              v-if="
                showEditAndDelete(item) &&
                props.activeType === 'followPlan' &&
                [CustomerFollowPlanStatusEnum.COMPLETED].includes(item.status) &&
                !item.converted
              "
              type="primary"
              class="text-btn-primary"
              quaternary
              @click="handleConvert(item)"
            >
              {{ t('common.convertPlanToRecord') }}
            </n-button>
            <n-button
              v-if="
                showEditAndDelete(item) &&
                (props.activeType === 'followRecord' ||
                  (props.activeType === 'followPlan' &&
                    ![CustomerFollowPlanStatusEnum.CANCELLED, CustomerFollowPlanStatusEnum.CANCELLED].includes(
                      item.status
                    )))
              "
              type="primary"
              class="text-btn-primary"
              quaternary
              @click="handleEdit(item)"
            >
              {{ t('common.edit') }}
            </n-button>
            <n-button
              v-if="showEditAndDelete(item)"
              type="error"
              class="text-btn-error"
              quaternary
              @click="handleDelete(item)"
            >
              {{ t('common.delete') }}
            </n-button>
          </div>
        </template>
        <template #createTime="{ descItem }">
          <div class="flex items-center gap-[8px]">
            {{ dayjs(descItem.value).format('YYYY-MM-DD HH:mm:ss') }}
          </div>
        </template>
        <template #updateTime="{ descItem }">
          <div class="flex items-center gap-[8px]">
            {{ dayjs(descItem.value).format('YYYY-MM-DD HH:mm:ss') }}
          </div>
        </template>
      </FollowRecord>
    </n-spin>
    <CrmFormCreateDrawer
      v-model:visible="formDrawerVisible"
      :form-key="realFormKey"
      :source-id="realFollowSourceId"
      :initial-source-name="props.initialSourceName"
      :need-init-detail="needInitDetail"
      :link-form-info="linkFormFieldMap"
      :link-form-key="linkFormKey"
      :link-scenario="linkScenario"
      :other-save-params="otherSaveParams"
      @saved="handleAfterSave"
    />

    <DetailDrawer
      v-model:show="showDetailDrawer"
      :form-key="realFormKey"
      :source-id="sourceId"
      :source-name="sourceName"
      :refresh-key="refreshDetailKey"
      :hide-edit-delete="activeType === 'followPlan' || activeType === 'followRecord'"
      @delete="handleDelete(activeItem as FollowDetailItem)"
      @edit="handleEdit(activeItem as FollowDetailItem)"
    />
  </div>
</template>

<script lang="ts" setup>
  import { NButton, NSpin } from 'naive-ui';
  import dayjs from 'dayjs';

  import { CustomerFollowPlanStatusEnum } from '@lib/shared/enums/customerEnum';
  import { FieldTypeEnum, FormDesignKeyEnum, FormLinkScenarioEnum } from '@lib/shared/enums/formDesignEnum';
  import { useI18n } from '@lib/shared/hooks/useI18n';
  import type { CustomerFollowPlanListItem, FollowDetailItem } from '@lib/shared/models/customer';

  import type { Description } from '@/components/pure/crm-detail-card/index.vue';
  import CrmSearchInput from '@/components/pure/crm-search-input/index.vue';
  import CrmTab from '@/components/pure/crm-tab/index.vue';
  import DetailDrawer from '@/components/business/crm-follow-drawer/components/detailDrawer.vue';
  import CrmFormCreateDrawer from '@/components/business/crm-form-create-drawer/index.vue';
  import FollowRecord from './followRecord.vue';

  import { getCustomerNextStage } from '@/api/modules';
  import useFormCreateApi from '@/hooks/useFormCreateApi';
  import { hasAnyPermission } from '@/utils/permission';

  import { descriptionList, statusTabList } from './config';
  import useFollowApi, { type followEnumType } from './useFollowApi';

  const { t } = useI18n();

  export type ActiveType = 'followPlan' | 'followRecord';

  interface FollowDetailProps {
    activeType: 'followRecord' | 'followPlan'; // 跟进记录|跟进计划
    followApiKey: followEnumType; // 跟进计划apiKey
    virtualScrollHeight?: string; // 虚拟高度
    wrapperClass?: string;
    sourceId: string; // 资源id
    refreshKey?: number;
    showAction?: boolean; // 显示操作
    initialSourceName?: string; // 初始化详情时的名称
    showAdd?: boolean; // 显示增加按钮
    anyPermission?: string[]; // 无任一权限展示无权限
    parentFormKey?: FormDesignKeyEnum; // 上级表单key
    customerStageStatus?: string; // 客户阶段状态（待/中/已）
    customerStage?: string; // 客户阶段ID（stage_fail/stage_payment）
  }

  const props = withDefaults(defineProps<FollowDetailProps>(), {
    showAction: true,
  });

  const emit = defineEmits<{
    (e: 'saved'): void;
  }>();

  const realFormKey = ref<FormDesignKeyEnum>(FormDesignKeyEnum.FOLLOW_PLAN_BUSINESS);
  const refreshDetailKey = ref(0);

  const sourceId = ref('');
  const sourceName = ref('');
  const showDetailDrawer = ref(false);
  const activeItem = ref<FollowDetailItem>();
  function handleDetail(row: FollowDetailItem) {
    sourceId.value = row.id;
    realFormKey.value =
      props.activeType === 'followRecord' ? FormDesignKeyEnum.FOLLOW_RECORD : FormDesignKeyEnum.FOLLOW_PLAN;
    sourceName.value = row.type === 'CLUE' && row.clueId?.length ? row.clueName : row.customerName;
    activeItem.value = row;
    showDetailDrawer.value = true;
  }

  const formDrawerVisible = ref(false);

  const {
    data,
    loading,
    handleReachBottom,
    searchData,
    activeStatus,
    loadFollowList,
    changePlanStatus,
    followKeyword,
    followFormKeyMap,
    handleDelete,
    getApiKey,
  } = useFollowApi({
    type: toRef(props, 'activeType'),
    followApiKey: props.followApiKey,
    sourceId: toRef(props, 'sourceId'),
    onDeleteSuccess: () => {
      // 确认删除成功后关闭详情弹窗
      if (showDetailDrawer.value) {
        showDetailDrawer.value = false;
      }
    },
  });

  const needInitDetail = ref(false);
  const activePlan = ref();
  const otherFollowRecordSaveParams = ref<Record<string, any>>({
    converted: false,
    customerStageStatus: '',
    customerStage: '',
  });

  const planFormSaveParams = ref<Record<string, any>>({ converted: false });

  watch(() => props.customerStageStatus, (val) => {
    if (val) {
      otherFollowRecordSaveParams.value.customerStageStatus = val;
    }
  }, { immediate: true });

  watch(() => props.customerStage, (val) => {
    if (val) {
      otherFollowRecordSaveParams.value.customerStage = val;
    }
  }, { immediate: true });

  const otherSaveParams = computed(() => {
    if (realFormKey.value === FormDesignKeyEnum.FOLLOW_PLAN_CUSTOMER) {
      return planFormSaveParams.value;
    }
    if (props.activeType === 'followPlan') {
      return otherFollowRecordSaveParams.value;
    }
    // 跟进记录场景：传递 customerStageStatus 和 customerStage
    if (props.activeType === 'followRecord') {
      const params: Record<string, any> = {};
      if (props.customerStageStatus) {
        params.customerStageStatus = props.customerStageStatus;
      }
      if (props.customerStage) {
        params.customerStage = props.customerStage;
      }
      return Object.keys(params).length ? params : undefined;
    }
    return undefined;
  });

  const linkFormKey = ref(FormDesignKeyEnum.FOLLOW_PLAN_BUSINESS);
  const linkScenario = ref(FormLinkScenarioEnum.PLAN_TO_RECORD);
  watch(
    () => props.parentFormKey,
    (val) => {
      if (val === FormDesignKeyEnum.CUSTOMER) {
        linkScenario.value = FormLinkScenarioEnum.CUSTOMER_TO_RECORD;
      }
      if (val === FormDesignKeyEnum.BUSINESS) {
        linkScenario.value = FormLinkScenarioEnum.OPPORTUNITY_TO_RECORD;
      }
      if (val === FormDesignKeyEnum.CLUE) {
        linkScenario.value = FormLinkScenarioEnum.CLUE_TO_RECORD;
      }
    },
    {
      immediate: true,
    }
  );
  const linkSourceId = computed(() => {
    if (linkScenario.value !== FormLinkScenarioEnum.PLAN_TO_RECORD) {
      return props.sourceId;
    }
    return activePlan.value?.id;
  });
  const { fieldList, formDetail, initFormDetail, initFormConfig, linkFormFieldMap, saveForm } = useFormCreateApi({
    formKey: computed(() => linkFormKey.value),
    sourceId: linkSourceId,
    needInitDetail: computed(() => needInitDetail.value),
    otherSaveParams: otherFollowRecordSaveParams,
  });

  onMounted(async () => {
    if (props.activeType === 'followRecord') {
      linkFormKey.value = FormDesignKeyEnum.FOLLOW_RECORD;
      await initFormConfig();
    }
  });

  function getDescriptionFun(item: FollowDetailItem) {
    const isClue = item.type === 'CLUE' && item.clueId?.length;
    const customerNameKey = isClue ? 'clueName' : 'customerName';
    let lastDescriptionList = [
      ...(props.followApiKey === 'myPlan' || props.activeType === 'followPlan'
        ? [
            {
              key: customerNameKey,
              label: isClue ? t('crmFollowRecord.companyName') : t('opportunity.customerName'),
              value: customerNameKey,
            },
          ]
        : []),
      ...descriptionList
        .filter((descItem) => {
          // 跟进记录场景下过滤掉 ownerName 和 processorName
          if (props.activeType === 'followRecord' && (descItem.key === 'ownerName' || descItem.key === 'processorName')) {
            return false;
          }
          return true;
        })
        .map((descriptionItem) => {
          // 无 formConfigField 的字段（如 processor）直接使用配置中的 label
          if (!descriptionItem.formConfigField) {
            return descriptionItem;
          }
          const label = fieldList.value.find((field) => field.businessKey === descriptionItem.formConfigField)?.name;
          return {
            ...descriptionItem,
            label,
          };
        }),
    ];

    if (isClue) {
      lastDescriptionList = lastDescriptionList.filter((e) => !['contactName', 'phone'].includes(e.key));
    }

    return (lastDescriptionList.map((desc) => ({
      ...desc,
      value: item[desc.key as keyof FollowDetailItem],
    })) || []) as Description[];
  }

  // 编辑记录或计划
  const realFollowSourceId = ref<string | undefined>('');
  const isConverted = ref(false);
  async function handleAdd() {
    activePlan.value = undefined;
    realFormKey.value =
      (followFormKeyMap[props.followApiKey as keyof typeof followFormKeyMap]?.[
        props.activeType
      ] as FormDesignKeyEnum) ?? realFormKey.value;
    realFollowSourceId.value = props.sourceId;
    needInitDetail.value = false;
    if (props.activeType === 'followPlan') {
      isConverted.value = false;
      const params: Record<string, any> = { converted: false, type: 'CUSTOMER' };
      if (props.sourceId && props.followApiKey === FormDesignKeyEnum.CUSTOMER) {
        try {
          const res = await getCustomerNextStage(props.sourceId);
          if (res) {
            params.customerId = props.sourceId;
            params.customerName = res.customerName || '';
            params.nextStage = res.nextStageId || '';
            params._nextStage = res.nextStageId || '';
            params.nextStageName = res.nextStageName || '';
            params.owner = res.owner || '';
            params.ownerName = res.ownerName || '';
          }
        } catch (error) {
          // eslint-disable-next-line no-console
          console.error('获取客户下一阶段信息失败', error);
        }
      }
      otherFollowRecordSaveParams.value = params;
      planFormSaveParams.value = { ...params };
    } else {
      linkFormKey.value = props.parentFormKey || linkFormKey.value;
      await initFormConfig();
      await initFormDetail(false, true);
    }
    formDrawerVisible.value = true;
  }

  async function handleConvert(item: FollowDetailItem) {
    linkScenario.value = FormLinkScenarioEnum.PLAN_TO_RECORD;
    activePlan.value = item;
    isConverted.value = true;
    otherFollowRecordSaveParams.value.converted = isConverted.value;
    realFollowSourceId.value = '';
    realFormKey.value =
      followFormKeyMap[getApiKey(item) as keyof typeof followFormKeyMap]?.followRecord ?? realFormKey.value;
    linkFormKey.value = FormDesignKeyEnum.FOLLOW_PLAN_CUSTOMER;
    needInitDetail.value = false;
    await initFormConfig();
    await initFormDetail(false, true);
    formDrawerVisible.value = true;
  }

  function handleEdit(item: FollowDetailItem) {
    realFormKey.value = followFormKeyMap[getApiKey(item) as keyof typeof followFormKeyMap]?.[
      props.activeType
    ] as FormDesignKeyEnum;
    realFollowSourceId.value = item.id;
    needInitDetail.value = true;
    formDrawerVisible.value = true;
    if (props.activeType === 'followPlan') {
      isConverted.value = false;
      otherFollowRecordSaveParams.value.converted = (item as CustomerFollowPlanListItem).converted;
    }
  }

  const emptyText = computed(() => {
    if (!hasAnyPermission(props.anyPermission)) {
      return t('common.noPermission');
    }
    return props.activeType === 'followPlan' ? t('crmFollowRecord.noFollowPlan') : t('crmFollowRecord.noFollowRecord');
  });

  const planPermission: Partial<Record<followEnumType, string[]>> = {
    [FormDesignKeyEnum.CLUE]: ['CLUE_MANAGEMENT:UPDATE'],
    [FormDesignKeyEnum.CUSTOMER]: ['CUSTOMER_MANAGEMENT:UPDATE'],
    [FormDesignKeyEnum.BUSINESS]: ['OPPORTUNITY_MANAGEMENT:UPDATE'],
  };

function getShowAction(item: FollowDetailItem) {
    // 跟进计划场景下隐藏编辑删除按钮
    if (props.activeType === 'followPlan') {
      return false;
    }
    if (props.followApiKey === 'myPlan') {
      const permission = planPermission[getApiKey(item) as keyof typeof followFormKeyMap];
      return hasAnyPermission(permission);
    }
    return props.showAction;
  }

function showEditAndDelete(item: FollowDetailItem) {
    if (props.activeType === 'followPlan' || props.activeType === 'followRecord') {
      return false;
    }
    return getShowAction(item);
  }

  // 更新计划为已转记录
  async function updatePlan() {
    linkFormKey.value = followFormKeyMap[getApiKey(activePlan.value) as keyof typeof followFormKeyMap]?.[
      props.activeType
    ] as FormDesignKeyEnum;
    realFollowSourceId.value = activePlan.value.id;
    needInitDetail.value = true;
    initFormConfig();
    await initFormDetail();
    fieldList.value.forEach((item) => {
      if (
        [FieldTypeEnum.DATA_SOURCE, FieldTypeEnum.MEMBER, FieldTypeEnum.DEPARTMENT].includes(item.type) &&
        Array.isArray(formDetail.value[item.id])
      ) {
        formDetail.value[item.id] = formDetail.value[item.id]?.[0];
      }
    });
    await saveForm(formDetail.value, false, () => ({}), true);
    isConverted.value = false;
    otherFollowRecordSaveParams.value.converted = isConverted.value;
    loadFollowList();
  }

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
    realFormKey.value = FormDesignKeyEnum.FOLLOW_PLAN_CUSTOMER;
    realFollowSourceId.value = customerId;
    needInitDetail.value = false;
    formDrawerVisible.value = true;
  }

  function handleAfterSave(res?: any) {
    if (realFormKey.value === FormDesignKeyEnum.FOLLOW_PLAN_CUSTOMER) {
      planFormSaveParams.value = { converted: false };
      loadFollowList();
      emit('saved');
      return;
    }
    if (isConverted.value) {
      updatePlan();
    } else {
      loadFollowList();
    }
    if (showDetailDrawer.value) {
      refreshDetailKey.value += 1;
    }
    if (res?.followResult === 'COMPLETED' && res?.customerId) {
      openPlanForm(res.customerId, res.opportunityId, res.contactId);
    }
    emit('saved');
  }

  watch(
    () => props.refreshKey,
    (val) => {
      if (val) {
        loadFollowList(true);
      }
    }
  );

  watch(
    () => props.activeType,
    () => {
      loadFollowList(true);
    },
    { immediate: true }
  );
</script>

<style lang="less" scoped>
  .crm-follow-detail {
    height: 100%;
    display: flex;
    flex-direction: column;
    border-radius: @border-radius-medium;
    background: var(--text-n10);
  }
  :deep(.n-spin) {
    flex: 1;
    min-height: 0;
    overflow: hidden;
  }
  :deep(.n-spin-container) {
    height: 100%;
    overflow: hidden;
  }
  :deep(.n-tabs) {
    width: auto;
  }
</style>
