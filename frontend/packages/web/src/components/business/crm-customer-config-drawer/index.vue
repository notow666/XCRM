<template>
  <CrmDrawer
    v-model:show="showDrawer"
    :width="600"
    :footer="false"
    :auto-focus="false"
    no-padding
    :show-back="false"
    closable
  >
    <template #title>
      <div class="flex items-center gap-[8px]">
        <div class="one-line-text">{{ title }}</div>
      </div>
    </template>
    <n-scrollbar content-class="p-[24px]">
      <div class="bg-[var(--text-n9)] p-[16px]">
        <CrmBatchForm
          ref="batchFormRef"
          class="!p-0"
          :models="formItemModel"
          :default-list="form.list"
          :add-text="addText"
          validate-when-add
          draggable
          :disabled-add="form.list.length >= 50"
          :pop-confirm-props="getConfirmPropsFun"
          @delete-row="handleDelete"
          @save-row="handleSave"
          @drag="dragEnd"
        />
      </div>
    </n-scrollbar>
  </CrmDrawer>
</template>

<script setup lang="ts">
  /* eslint-disable simple-import-sort/imports */
  import { ref } from 'vue';
  import { NScrollbar, useMessage } from 'naive-ui';

  import { FieldTypeEnum } from '@lib/shared/enums/formDesignEnum';
  import { useI18n } from '@lib/shared/hooks/useI18n';

  import CrmBatchForm from '@/components/business/crm-batch-form/index.vue';
  import CrmDrawer from '@/components/pure/crm-drawer/index.vue';

  import {
    addCustomerFailReason,
    addCustomerFollowWay,
    deleteCustomerFailReason,
    deleteCustomerFollowWay,
    getCustomerFailReasonList,
    getCustomerFollowWayList,
    updateCustomerFailReason,
    updateCustomerFollowWay,
  } from '@/api/modules';

  const { t } = useI18n();
  const Message = useMessage();

  const props = defineProps<{
    title: string;
    type: 'failReason' | 'followWay';
  }>();

  const showDrawer = defineModel<boolean>('visible', {
    required: true,
  });

  const title = computed(() => props.title);

  const addText = computed(() =>
    props.type === 'failReason' ? t('crmReasonDrawer.addFailReason') : t('crmReasonDrawer.addFollowWay')
  );

  const form = ref<any>({ list: [] });

  const getListApi = computed(() =>
    props.type === 'failReason' ? getCustomerFailReasonList : getCustomerFollowWayList
  );
  const addApi = computed(() => (props.type === 'failReason' ? addCustomerFailReason : addCustomerFollowWay));
  const updateApi = computed(() => (props.type === 'failReason' ? updateCustomerFailReason : updateCustomerFollowWay));
  const deleteApi = computed(() => (props.type === 'failReason' ? deleteCustomerFailReason : deleteCustomerFollowWay));

  const formItemModel = ref([
    {
      path: 'name',
      type: FieldTypeEnum.INPUT,
      formItemClass: 'w-full flex-initial',
      inputProps: {
        maxlength: 255,
      },
      rule: [
        {
          required: true,
          message: t('common.notNull', { value: props.title }),
        },
        { notRepeat: true, message: t('module.capacitySet.repeatMsg') },
      ],
    },
  ]);

  const popConfirmLoading = ref(false);
  function getConfirmPropsFun(_: Record<string, any>, i: number) {
    return {
      title: t('crmReasonDrawer.deleteTitleTip', { title: props.title }),
      content: t('crmReasonDrawer.deleteContentTip'),
      positiveText: t('common.remove'),
      disabled: form.value.list.length === 1 && i === 0,
      loading: popConfirmLoading.value,
    };
  }

  const loading = ref(false);
  async function initList() {
    try {
      loading.value = true;
      const res = await getListApi.value();
      form.value.list = res ?? [];
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
    } finally {
      loading.value = false;
    }
  }

  async function handleDelete(_i: number, id: string, done: () => void) {
    if (form.value.list.length === 1) return;
    try {
      popConfirmLoading.value = true;
      await deleteApi.value(id);
      done();
      initList();
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
    } finally {
      popConfirmLoading.value = false;
    }
  }

  async function handleSave(element: Record<string, any>, done: () => void) {
    try {
      if (element.id) {
        await updateApi.value({ id: element.id, name: element.name });
        done();
      } else {
        await addApi.value({ name: element.name });
        done();
        await initList();
      }
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
    }
  }

  async function dragEnd() {
    Message.success(t('common.operationSuccess'));
  }

  watch(
    () => showDrawer.value,
    (val) => {
      if (val) {
        initList();
      }
    }
  );
</script>

<style scoped></style>
