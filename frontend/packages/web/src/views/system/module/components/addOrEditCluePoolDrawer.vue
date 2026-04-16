<template>
  <CrmDrawer
    v-model:show="visible"
    :width="900"
    :title="title"
    :show-continue="!form.id"
    :ok-text="form.id ? t('common.update') : undefined"
    :loading="loading"
    @confirm="confirmHandler(false)"
    @continue="confirmHandler(true)"
    @cancel="cancelHandler"
  >
    <n-scrollbar>
      <n-alert v-if="form.id" class="mb-[16px]" type="warning">
        {{ t('module.clue.updateConfirmContent') }}
      </n-alert>
      <n-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-placement="left"
        :label-width="110"
        require-mark-placement="left"
      >
        <div class="crm-module-form-title">{{ t('common.baseInfo') }}</div>
        <div class="w-full">
          <n-form-item
            path="name"
            :label="
              props.type === ModuleConfigEnum.CLUE_MANAGEMENT ? t('module.clue.name') : t('module.customer.openSeaName')
            "
          >
            <n-input v-model:value="form.name" :maxlength="255" type="text" :placeholder="t('common.pleaseInput')" />
          </n-form-item>
        </div>

        <template v-if="props.type === ModuleConfigEnum.CLUE_MANAGEMENT">
          <div class="crm-module-form-title mt-[24px]">
            {{ t('module.clue.clueDistributeRule') }}
          </div>
          <n-form-item path="distribute" :label="t('module.clue.autoDistribute')">
            <n-radio-group v-model:value="form.distribute" name="radiogroup-distribute">
              <n-space>
                <n-radio :value="true">
                  {{ t('common.yes') }}
                </n-radio>
                <n-radio :value="false">
                  {{ t('common.no') }}
                </n-radio>
              </n-space>
            </n-radio-group>
          </n-form-item>
          <n-form-item
            v-if="form.distribute"
            :label="t('module.clue.targetCustomerPool')"
            path="distributeRule.customerPoolId"
            :rule="{
              required: true,
              message: t('common.pleaseSelect'),
              trigger: ['change', 'blur'],
            }"
          >
            <n-select
              v-model:value="form.distributeRule!.customerPoolId"
              :options="customerPoolOptions"
              :placeholder="t('common.pleaseSelect')"
              filterable
              clearable
              :loading="customerPoolLoading"
            />
          </n-form-item>
          <FilterContent
            v-if="form.distribute"
            ref="distributeFilterContentRef"
            v-model:form-model="distributeFormViewModel as FilterForm"
            keep-one-line
            :config-list="advanceFilterConfigList"
            :custom-list="customFieldsFilterConfig"
          />
        </template>

        <div class="crm-module-form-title mt-[24px]">
          {{ t('module.clue.columnsSetting') }}
        </div>
        <n-checkbox-group v-model:value="showFieldIds" class="grid grid-cols-5 gap-[12px]">
          <n-checkbox
            v-for="field in showInTableColumns"
            :key="field.id"
            :value="field.id"
            :label="field.name"
            :disabled="field.businessKey === 'name'"
          />
        </n-checkbox-group>
      </n-form>
    </n-scrollbar>
  </CrmDrawer>
</template>

<script setup lang="ts">
  import { computed, ref, watch } from 'vue';
  import {
    FormInst,
    FormRules,
    NAlert,
    NCheckbox,
    NCheckboxGroup,
    NForm,
    NFormItem,
    NInput,
    NRadio,
    NRadioGroup,
    NScrollbar,
    NSelect,
    NSpace,
    NTooltip,
    useMessage,
  } from 'naive-ui';
  import { cloneDeep } from 'lodash-es';

  import { OperatorEnum } from '@lib/shared/enums/commonEnum';
  import { FieldTypeEnum, FormDesignKeyEnum } from '@lib/shared/enums/formDesignEnum';
  import { ModuleConfigEnum } from '@lib/shared/enums/moduleEnum';
  import { useI18n } from '@lib/shared/hooks/useI18n';
  import type {
    CluePoolForm,
    CluePoolItem,
    CluePoolParams,
    ModuleConditionsItem,
  } from '@lib/shared/models/system/module';

  import FilterContent from '@/components/pure/crm-advance-filter/components/filterContent.vue';
  import { FIXED } from '@/components/pure/crm-advance-filter/index';
  import { AccordBelowType, FilterForm, FilterFormItem, FilterResult } from '@/components/pure/crm-advance-filter/type';
  import CrmDrawer from '@/components/pure/crm-drawer/index.vue';
  import CrmInputNumber from '@/components/pure/crm-input-number/index.vue';
  import { multipleValueTypeList } from '@/components/business/crm-form-create/config';
  import CrmUserTagSelector from '@/components/business/crm-user-tag-selector/index.vue';

  import {
    addCluePool,
    addCustomerPool,
    getCustomerPoolListByEnable,
    updateCluePool,
    updateCustomerPool,
  } from '@/api/modules';
  import { baseFilterConfigList } from '@/config/clue';
  import useFormCreateAdvanceFilter from '@/hooks/useFormCreateAdvanceFilter';
  import useFormCreateApi from '@/hooks/useFormCreateApi';
  import useReasonConfig from '@/hooks/useReasonConfig';

  const { t } = useI18n();
  const Message = useMessage();

  const props = defineProps<{
    type: ModuleConfigEnum;
    quick?: boolean;
    row?: CluePoolItem;
  }>();

  const visible = defineModel<boolean>('visible', {
    required: true,
  });

  const emit = defineEmits<{
    (e: 'refresh'): void;
    (e: 'saved'): void;
  }>();

  const tabName = ref('baseInfo');
  const formKey = computed(() => {
    return props.type === ModuleConfigEnum.CLUE_MANAGEMENT
      ? FormDesignKeyEnum.CLUE_POOL
      : FormDesignKeyEnum.CUSTOMER_OPEN_SEA;
  });
  const { fieldList, initFormConfig, moduleFormConfig } = useFormCreateApi({
    formKey,
  });
  const { getFilterListConfig, customFieldsFilterConfig } = useFormCreateAdvanceFilter();
  const { reasonOptions: cluePoolReasonOptions, initReasonConfig: initCluePoolReasonConfig } = useReasonConfig(
    FormDesignKeyEnum.CLUE_POOL
  );
  const { reasonOptions: openSeaReasonOptions, initReasonConfig: initOpenSeaReasonConfig } = useReasonConfig(
    FormDesignKeyEnum.CUSTOMER_OPEN_SEA
  );
  const showInTableColumns = computed(() => {
    return fieldList.value.filter(
      (item) => ![FieldTypeEnum.DIVIDER, FieldTypeEnum.TEXTAREA].includes(item.type) && item.businessKey !== 'owner'
    );
  });
  const rules: FormRules = {
    name: [
      {
        required: true,
        message: t('common.notNull', {
          value: `${
            props.type === ModuleConfigEnum.CLUE_MANAGEMENT ? t('module.clue.name') : t('module.customer.openSeaName')
          }`,
        }),
        trigger: ['input', 'blur'],
      },
    ],
    adminIds: [{ required: false, message: t('common.pleaseSelect') }],
    userIds: [{ required: false, message: t('common.pleaseSelect') }],
    [`pickRule.pickIntervalDays`]: [
      { required: true, type: 'number', message: t('common.pleaseInput'), trigger: ['input', 'blur'] },
    ],
    [`pickRule.pickNumber`]: [
      { required: true, type: 'number', message: t('common.pleaseInput'), trigger: ['input', 'blur'] },
    ],
    [`pickRule.newPickInterval`]: [
      { required: true, type: 'number', message: t('common.pleaseInput'), trigger: ['input', 'blur'] },
    ],
  };

  const initForm: CluePoolForm = {
    name: '',
    adminIds: [],
    userIds: [],
    enable: true,
    auto: false,
    distribute: false,
    pickRule: {
      limitOnNumber: false,
      pickNumber: undefined,
      limitPreOwner: false,
      pickIntervalDays: undefined,
      limitNew: false,
      newPickInterval: undefined,
    },
    recycleRule: {
      operator: 'all',
      conditions: [],
    },
    distributeRule: {
      customerPoolId: '',
      combineSearch: {
        searchMode: 'AND',
        conditions: [],
      },
    },
    hiddenFieldIds: [],
  };
  const showFieldIds = ref<string[]>([]);
  const form = ref<CluePoolForm>(cloneDeep(initForm));

  const defaultFormModel: FilterForm = {
    searchMode: 'AND',
    list: [
      {
        dataIndex: 'storageTime',
        type: FieldTypeEnum.TIME_RANGE_PICKER,
        operator: OperatorEnum.DYNAMICS,
        showScope: true,
        scope: ['Created', 'Picked'],
      },
    ],
  };
  const defaultDistributeFormModel: FilterForm = {
    searchMode: 'AND',
    list: [{ dataIndex: null, operator: undefined, value: null, type: FieldTypeEnum.INPUT }],
  };
  const recycleFormItemModel = ref<FilterForm>(cloneDeep(defaultFormModel));
  const distributeFilterResult = ref<FilterResult>({ searchMode: 'AND', conditions: [] });
  const distributeFormViewModel = ref<FilterForm>(cloneDeep(defaultDistributeFormModel));

  const customerPoolOptions = ref<{ label: string; value: string }[]>([]);
  const customerPoolLoading = ref(false);

  async function loadCustomerPoolOptions() {
    if (props.type !== ModuleConfigEnum.CLUE_MANAGEMENT) {
      return;
    }
    customerPoolLoading.value = true;
    try {
      const res = await getCustomerPoolListByEnable();
      const list: any[] = res ?? [];
      customerPoolOptions.value = list.filter((p) => p.enable).map((p) => ({ label: p.name, value: p.id }));
    } catch (e) {
      // eslint-disable-next-line no-console
      console.error(e);
      customerPoolOptions.value = [];
    } finally {
      customerPoolLoading.value = false;
    }
  }

  const title = computed(() => {
    if (props.type === ModuleConfigEnum.CLUE_MANAGEMENT) {
      return !form.value.id ? t('module.clue.addCluePool') : t('module.clue.updateCluePool');
    }
    if (props.type === ModuleConfigEnum.CUSTOMER_MANAGEMENT) {
      return !form.value.id ? t('module.customer.addOpenSea') : t('module.customer.updateOpenSea');
    }
  });

  const advanceFilterConfigList = computed<FilterFormItem[]>(() => {
    return [...baseFilterConfigList];
  });

  function getColumnMetaByDataIndex(dataIndex: string): FilterFormItem | undefined {
    const system = advanceFilterConfigList.value;
    for (let i = 0; i < system.length; i++) {
      if (system[i].dataIndex === dataIndex) {
        return system[i];
      }
    }
    const customList = customFieldsFilterConfig.value as FilterFormItem[];
    for (let i = 0; i < customList.length; i++) {
      if (customList[i].dataIndex === dataIndex) {
        return customList[i];
      }
    }
    return undefined;
  }

  function conditionsToFilterItems(conditions: ModuleConditionsItem[] | undefined): FilterFormItem[] {
    if (!conditions?.length) {
      return [];
    }
    return conditions.map((item): FilterFormItem => {
      const meta = getColumnMetaByDataIndex(item.column);
      const base: FilterFormItem = {
        dataIndex: item.column,
        operator: item.operator as OperatorEnum,
        value: item.value,
        scope: item.scope,
        showScope: !!item.scope?.length,
        type: meta?.type ?? FieldTypeEnum.TIME_RANGE_PICKER,
      };
      return meta ? ({ ...meta, ...base, type: meta.type ?? FieldTypeEnum.TIME_RANGE_PICKER } as FilterFormItem) : base;
    });
  }

  function normalizeCombineSearch(combineSearch: any): FilterResult {
    return {
      searchMode: (combineSearch?.searchMode ?? 'AND') as AccordBelowType,
      conditions: (combineSearch?.conditions ?? []).map((item: any) => ({
        name: item.name,
        operator: item.operator as OperatorEnum,
        value: item.value,
        multipleValue: !!item.multipleValue,
        type: item.type as FieldTypeEnum,
      })),
    };
  }

  function filterResultToFormModel(filter: FilterResult): FilterForm {
    const list =
      filter.conditions?.map((item) => {
        const meta = getColumnMetaByDataIndex(item.name || '');
        return {
          ...(meta || {}),
          dataIndex: item.name || '',
          operator: item.operator as OperatorEnum,
          value: item.value,
          type: (item.type as FieldTypeEnum) ?? meta?.type ?? FieldTypeEnum.TIME_RANGE_PICKER,
        } as FilterFormItem;
      }) ?? [];
    return {
      searchMode: (filter.searchMode ?? 'AND') as AccordBelowType,
      list: list.length ? list : cloneDeep(defaultDistributeFormModel.list),
    };
  }

  function filterFormToResult(formModel: FilterForm): FilterResult {
    return {
      searchMode: formModel.searchMode,
      conditions: (formModel.list || []).map((item: any) => ({
        name: item.dataIndex ?? '',
        operator: item.operator,
        value: item.value,
        multipleValue: multipleValueTypeList.includes(item.type),
        type: item.type,
      })),
    };
  }

  function cancelHandler() {
    form.value = cloneDeep(initForm);
    recycleFormItemModel.value = cloneDeep(defaultFormModel);
    distributeFilterResult.value = { searchMode: 'AND', conditions: [] };
    distributeFormViewModel.value = cloneDeep(defaultDistributeFormModel);
    visible.value = false;
  }

  const formRef = ref<FormInst | null>(null);
  const loading = ref<boolean>(false);

  async function handleSave(isContinue: boolean) {
    try {
      loading.value = true;
      const { userIds, auto, distribute, adminIds, distributeRule: _formDistributeRule, ...otherParams } = form.value;

      const params: CluePoolParams = {
        ...otherParams,
        distribute: props.type === ModuleConfigEnum.CLUE_MANAGEMENT ? distribute : false,
        ownerIds: adminIds.map((e) => e.id),
        scopeIds: userIds.map((e) => e.id),
        auto,
        recycleRule: {
          operator: recycleFormItemModel.value.searchMode as string,
          conditions: [],
        },
        hiddenFieldIds: showInTableColumns.value
          .filter((item) => !showFieldIds.value.includes(item.id))
          .map((item) => item.id),
      };
      if (auto) {
        const conditions: ModuleConditionsItem[] = [];
        recycleFormItemModel.value.list?.forEach((item) => {
          conditions.push({
            column: item.dataIndex || '',
            operator: item.operator || '',
            value: item.value,
            scope: item.scope,
          });
        });
        params.recycleRule.conditions = form.value.auto ? conditions : [];
      }
      if (props.type === ModuleConfigEnum.CLUE_MANAGEMENT) {
        const currentDistributeForm = distributeFormViewModel.value as FilterForm;
        distributeFilterResult.value = distribute
          ? (filterFormToResult(currentDistributeForm) as FilterResult)
          : { searchMode: 'AND', conditions: [] };
        const combineSearch: any = distribute ? distributeFilterResult.value : { searchMode: 'AND', conditions: [] };
        params.distributeRule = {
          customerPoolId: distribute ? form.value.distributeRule?.customerPoolId ?? '' : '',
          combineSearch,
        };
      }
      if (form.value.id) {
        await (props.type === ModuleConfigEnum.CUSTOMER_MANAGEMENT
          ? updateCustomerPool(params, props.quick)
          : updateCluePool(params, props.quick));
        Message.success(t('common.updateSuccess'));
        emit('saved');
      } else {
        await (props.type === ModuleConfigEnum.CUSTOMER_MANAGEMENT ? addCustomerPool(params) : addCluePool(params));
        Message.success(t('common.addSuccess'));
        emit('refresh');
      }
      if (isContinue) {
        form.value = cloneDeep(initForm);
        recycleFormItemModel.value = cloneDeep(defaultFormModel);
        distributeFilterResult.value = { searchMode: 'AND', conditions: [] };
        distributeFormViewModel.value = cloneDeep(defaultDistributeFormModel);
      } else {
        cancelHandler();
      }
    } catch (e) {
      // eslint-disable-next-line no-console
      console.log(e);
    } finally {
      loading.value = false;
    }
  }

  const filterContentRef = ref<InstanceType<typeof FilterContent>>();
  const distributeFilterContentRef = ref<InstanceType<typeof FilterContent>>();

  function confirmHandler(isContinue: boolean) {
    formRef.value?.validate(async (error) => {
      if (error) {
        return;
      }
      const runDistributeFilter = () => {
        if (
          props.type === ModuleConfigEnum.CLUE_MANAGEMENT &&
          form.value.distribute &&
          distributeFilterContentRef.value
        ) {
          distributeFilterContentRef.value.formRef?.validate((distErrors) => {
            if (!distErrors) {
              handleSave(isContinue);
            }
          });
        } else {
          handleSave(isContinue);
        }
      };
      if (form.value.auto && filterContentRef.value) {
        filterContentRef.value.formRef?.validate((recycleErrors) => {
          if (!recycleErrors) {
            runDistributeFilter();
          }
        });
      } else {
        runDistributeFilter();
      }
    });
  }

  watch([() => props.row, () => visible.value], () => {
    if (props.row && visible.value) {
      const val = props.row;
      form.value = {
        id: val.id,
        name: val.name,
        enable: val.enable,
        auto: val.auto,
        distribute: val.distribute ?? false,
        pickRule: val.pickRule ?? cloneDeep(initForm).pickRule,
        recycleRule: val.recycleRule ?? cloneDeep(initForm).recycleRule,
        distributeRule: val.distributeRule ?? cloneDeep(initForm).distributeRule!,
        userIds: val.members,
        adminIds: val.owners,
        hiddenFieldIds: val.fieldConfigs?.filter((item) => !item.enable).map((item) => item.fieldId) || [],
      };
      if (val.auto) {
        const mapped = conditionsToFilterItems(val.recycleRule.conditions);
        recycleFormItemModel.value = {
          list: mapped.length ? mapped : cloneDeep(defaultFormModel.list),
          searchMode: val.recycleRule.operator as AccordBelowType,
        };
      } else {
        recycleFormItemModel.value = cloneDeep(defaultFormModel);
      }
      if (val.distribute) {
        const combineSearch = normalizeCombineSearch(val.distributeRule?.combineSearch);
        const conditions = combineSearch?.conditions ?? [];
        const mapped = conditionsToFilterItems(
          conditions.map((item) => ({
            column: item.name || '',
            operator: (item.operator as string) || '',
            value: item.value,
            scope: ['Created'],
          }))
        );
        distributeFilterResult.value = {
          ...combineSearch,
          conditions: combineSearch.conditions?.length
            ? combineSearch.conditions
            : filterFormToResult({
                list: mapped.length ? mapped : cloneDeep(defaultFormModel.list),
                searchMode: (combineSearch?.searchMode ?? defaultFormModel.searchMode) as AccordBelowType,
              }).conditions,
        };
        distributeFormViewModel.value = filterResultToFormModel(distributeFilterResult.value);
      } else {
        distributeFilterResult.value = { searchMode: 'AND', conditions: [] };
        distributeFormViewModel.value = cloneDeep(defaultDistributeFormModel);
      }
    }
  });

  watch(
    () => visible.value,
    async (val) => {
      if (val) {
        tabName.value = 'baseInfo';
        if (props.type === ModuleConfigEnum.CLUE_MANAGEMENT) {
          await initCluePoolReasonConfig();
        } else {
          await initOpenSeaReasonConfig();
        }
        await initFormConfig();
        if (moduleFormConfig.value) {
          customFieldsFilterConfig.value = getFilterListConfig(moduleFormConfig.value);
        }
        if (props.type === ModuleConfigEnum.CLUE_MANAGEMENT) {
          await loadCustomerPoolOptions();
        }
        showFieldIds.value = showInTableColumns.value
          .filter((item) => !form.value.hiddenFieldIds.includes(item.id))
          .map((item) => item.id);
        if (props.row && visible.value) {
          const { row } = props;
          if (row.auto) {
            const mapped = conditionsToFilterItems(row.recycleRule.conditions);
            recycleFormItemModel.value = {
              list: mapped.length ? mapped : cloneDeep(defaultFormModel.list),
              searchMode: row.recycleRule.operator as AccordBelowType,
            };
          }
          if (row.distribute) {
            const combineSearch = normalizeCombineSearch(row.distributeRule?.combineSearch);
            const conditions = combineSearch?.conditions ?? [];
            const mapped = conditionsToFilterItems(
              conditions.map((item) => ({
                column: item.name || '',
                operator: (item.operator as string) || '',
                value: item.value,
                scope: ['Created'],
              }))
            );
            distributeFilterResult.value = {
              ...combineSearch,
              conditions: combineSearch.conditions?.length
                ? combineSearch.conditions
                : filterFormToResult({
                    list: mapped.length ? mapped : cloneDeep(defaultFormModel.list),
                    searchMode: (combineSearch?.searchMode ?? defaultFormModel.searchMode) as AccordBelowType,
                  }).conditions,
            };
            distributeFormViewModel.value = filterResultToFormModel(distributeFilterResult.value);
          } else {
            distributeFilterResult.value = { searchMode: 'AND', conditions: [] };
            distributeFormViewModel.value = cloneDeep(defaultDistributeFormModel);
          }
        }
      }
    }
  );
</script>

<style scoped lang="less">
  :deep(.dataIndex-col) {
    width: 100px;
    flex: initial;
  }
</style>
