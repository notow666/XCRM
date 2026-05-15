<template>
  <n-scrollbar x-scrollable :content-style="{ 'min-width': '1000px', 'width': '100%', 'height': '100%' }">
    <CrmCard hide-footer no-content-padding :special-height="licenseStore.expiredDuring ? 64 : 0">
      <div class="mb-[16px] flex flex-wrap items-center justify-between gap-[12px] px-[16px] pt-[16px]">
        <div class="flex shrink-0 flex-wrap items-center gap-[12px]">
          <n-button v-permission="['MMBA_DEVICE:ADD']" type="primary" @click="openForm()">
            {{ t('mmbaDevice.add') }}
          </n-button>
          <n-upload
            v-permission="['MMBA_DEVICE:IMPORT']"
            class="n-button n-button--primary-type n-button--medium-type"
            :show-file-list="false"
            accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            :custom-request="handleImport"
          >
            <n-button>{{ t('mmbaDevice.import') }}</n-button>
          </n-upload>
          <n-button v-permission="['MMBA_DEVICE:READ']" type="primary" ghost @click="handleSyncOrRefreshClick">
            {{ syncUiAwaitingRefresh ? t('mmbaDevice.refresh') : t('mmbaDevice.sync') }}
          </n-button>
        </div>
        <div class="flex min-w-0 flex-1 items-center justify-end gap-[12px]">
          <CrmSearchInput
            v-model:value="keyword"
            class="!w-[240px]"
            :placeholder="t('mmbaDevice.keyword')"
            @search="handleKeywordSearch"
          />
        </div>
      </div>
      <n-data-table
        :columns="columns"
        :data="tableData"
        :loading="loading"
        :bordered="false"
        :scroll-x="1500"
        class="px-[16px]"
      />
      <div class="flex justify-end px-[16px] py-[16px]">
        <n-pagination
          v-model:page="page.current"
          v-model:page-size="page.pageSize"
          :item-count="total"
          :page-sizes="[10, 20, 50]"
          show-size-picker
          @update:page="load"
          @update:page-size="onPageSizeChange"
        />
      </div>
    </CrmCard>
  </n-scrollbar>

  <n-modal
    v-model:show="showModal"
    preset="dialog"
    :title="isEdit ? t('mmbaDevice.modal.editTitle') : t('mmbaDevice.modal.addTitle')"
    :positive-text="t('common.confirm')"
    :negative-text="t('common.cancel')"
    style="width: 960px"
    @positive-click="handleSubmit"
  >
    <n-form ref="formRef" :model="form" :rules="rules" label-placement="top" class="mt-[12px]">
      <div class="grid grid-cols-2 gap-x-[20px] gap-y-[2px]">
        <n-form-item :label="t('mmbaDevice.form.id')" path="id">
          <n-input v-model:value="form.id" size="small" :disabled="isEdit" />
        </n-form-item>
        <n-form-item :label="t('mmbaDevice.form.staffName')" path="staffName">
          <n-input v-model:value="form.staffName" size="small" />
        </n-form-item>
        <n-form-item :label="t('mmbaDevice.form.deviceId')" path="deviceId">
          <n-input v-model:value="form.deviceId" size="small" />
        </n-form-item>
        <n-form-item :label="t('mmbaDevice.form.deviceStatus')" path="deviceStatus">
          <n-select
            v-model:value="form.deviceStatus"
            size="small"
            class="w-full"
            clearable
            :options="deviceStatusOptions"
          />
        </n-form-item>
        <n-form-item :label="t('mmbaDevice.form.deviceName')" path="deviceName">
          <n-input v-model:value="form.deviceName" size="small" />
        </n-form-item>
        <n-form-item :label="t('mmbaDevice.form.deviceType')" path="deviceType">
          <n-input v-model:value="form.deviceType" size="small" />
        </n-form-item>
      </div>
      <div class="mt-[10px] grid grid-cols-4 gap-x-[12px] gap-y-[2px]">
        <n-form-item :label="t('mmbaDevice.form.imei')" path="imei">
          <n-input v-model:value="form.imei" size="small" />
        </n-form-item>
        <n-form-item :label="t('mmbaDevice.form.iccid')" path="iccid">
          <n-input v-model:value="form.iccid" size="small" />
        </n-form-item>
        <n-form-item :label="t('mmbaDevice.form.phone')" path="phone">
          <n-input v-model:value="form.phone" size="small" />
        </n-form-item>
        <n-form-item :label="t('mmbaDevice.form.telecomOperators')" path="telecomOperators">
          <n-input v-model:value="form.telecomOperators" size="small" />
        </n-form-item>
      </div>
      <div class="grid grid-cols-4 gap-x-[12px] gap-y-[2px]">
        <n-form-item :label="t('mmbaDevice.form.imei2')" path="imei2">
          <n-input v-model:value="form.imei2" size="small" />
        </n-form-item>
        <n-form-item :label="t('mmbaDevice.form.iccid2')" path="iccid2">
          <n-input v-model:value="form.iccid2" size="small" />
        </n-form-item>
        <n-form-item :label="t('mmbaDevice.form.phone2')" path="phone2">
          <n-input v-model:value="form.phone2" size="small" />
        </n-form-item>
        <n-form-item :label="t('mmbaDevice.form.telecomOperators2')" path="telecomOperators2">
          <n-input v-model:value="form.telecomOperators2" size="small" />
        </n-form-item>
      </div>
    </n-form>
  </n-modal>
</template>

<script setup lang="ts">
  import { computed, h, onMounted, onUnmounted, reactive, ref } from 'vue';
  import { useRoute } from 'vue-router';
  import {
    type DataTableColumns,
    type FormInst,
    type FormRules,
    NButton,
    NDataTable,
    NForm,
    NFormItem,
    NInput,
    NModal,
    NPagination,
    NScrollbar,
    NSelect,
    NUpload,
    type UploadCustomRequestOptions,
    useMessage,
  } from 'naive-ui';
  import dayjs from 'dayjs';

  import { MMBA_DEVICE_SYNC_DOM_EVENT } from '@lib/shared/constants/sseEventType';
  import { useI18n } from '@lib/shared/hooks/useI18n';
  import type { MmbaDevice } from '@lib/shared/models/mmba/device';

  import CrmCard from '@/components/pure/crm-card/index.vue';
  import CrmSearchInput from '@/components/pure/crm-search-input/index.vue';

  import { addMmbaDevice, getMmbaDevicePage, importMmbaDevice, syncMmbaDevices, updateMmbaDevice } from '@/api/modules';
  import useModal from '@/hooks/useModal';
  import useLicenseStore from '@/store/modules/setting/license';
  import { hasAnyPermission } from '@/utils/permission';

  import { SystemRouteEnum } from '@/enums/routeEnum';

  const { t } = useI18n();
  const message = useMessage();
  const { openModal } = useModal();
  const licenseStore = useLicenseStore();
  const route = useRoute();

  const keyword = ref('');
  /** 同步任务完成后，在本页将「同步」切换为「刷新」直至用户刷新列表 */
  const syncUiAwaitingRefresh = ref(false);
  const loading = ref(false);
  const tableData = ref<MmbaDevice[]>([]);
  const total = ref(0);
  const page = reactive({ current: 1, pageSize: 20 });

  const showModal = ref(false);
  const isEdit = ref(false);
  const editingId = ref('');
  const formRef = ref<FormInst | null>(null);
  const form = reactive({
    id: '',
    deviceId: '',
    staffName: '',
    deviceName: '',
    deviceType: '',
    imei: '',
    imei2: '',
    iccid: '',
    iccid2: '',
    phone: '',
    phone2: '',
    telecomOperators: '',
    telecomOperators2: '',
    deviceStatus: null as number | null,
  });

  const rules = computed<FormRules>(() => ({
    id: [
      {
        required: true,
        trigger: ['blur', 'input'],
        validator: (_rule, value: string) => {
          if (String(value ?? '').trim().length > 0) {
            return true;
          }
          return new Error(t('mmbaDevice.form.idRequired'));
        },
      },
    ],
  }));

  const deviceStatusOptions = computed(() =>
    [1, 3, 4, 5, 6, 7, 8, 9].map((v) => ({
      value: v,
      label: t(`mmbaDevice.deviceStatus.${v}`),
    }))
  );

  function formatTime(v?: number) {
    return v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-';
  }

  /** 列表展示：合并主号与副号，与导入「英文逗号」拆分语义一致 */
  function formatMergedPhones(row: MmbaDevice) {
    const p1 = row.phone?.trim();
    const p2 = row.phone2?.trim();
    if (p1 && p2) {
      return `${p1}, ${p2}`;
    }
    if (p1) {
      return p1;
    }
    if (p2) {
      return p2;
    }
    return '-';
  }

  function formatDeviceStatus(v?: number | null) {
    if (v == null) {
      return '-';
    }
    switch (v) {
      case 1:
        return t('mmbaDevice.deviceStatus.1');
      case 3:
        return t('mmbaDevice.deviceStatus.3');
      case 4:
        return t('mmbaDevice.deviceStatus.4');
      case 5:
        return t('mmbaDevice.deviceStatus.5');
      case 6:
        return t('mmbaDevice.deviceStatus.6');
      case 7:
        return t('mmbaDevice.deviceStatus.7');
      case 8:
        return t('mmbaDevice.deviceStatus.8');
      case 9:
        return t('mmbaDevice.deviceStatus.9');
      default:
        return String(v);
    }
  }

  function formatLoginStatus(v?: number | null) {
    if (v == null) {
      return '-';
    }
    if (v === 1) {
      return t('mmbaDevice.loginStatus.1');
    }
    if (v === 0) {
      return t('mmbaDevice.loginStatus.0');
    }
    return String(v);
  }

  function formatUserEnable(v?: boolean | null) {
    if (v === true) {
      return t('mmbaDevice.userStatus.enabled');
    }
    if (v === false) {
      return t('mmbaDevice.userStatus.disabled');
    }
    return '-';
  }

  function resetForm() {
    form.id = '';
    form.deviceId = '';
    form.staffName = '';
    form.deviceName = '';
    form.deviceType = '';
    form.imei = '';
    form.imei2 = '';
    form.iccid = '';
    form.iccid2 = '';
    form.phone = '';
    form.phone2 = '';
    form.telecomOperators = '';
    form.telecomOperators2 = '';
    form.deviceStatus = null;
  }

  function openForm(row?: MmbaDevice) {
    resetForm();
    if (row) {
      isEdit.value = true;
      editingId.value = row.id;
      form.id = row.id ?? '';
      form.deviceId = row.deviceId ?? '';
      form.staffName = row.staffName ?? '';
      form.deviceName = row.deviceName ?? '';
      form.deviceType = row.deviceType ?? '';
      form.imei = row.imei ?? '';
      form.imei2 = row.imei2 ?? '';
      form.iccid = row.iccid ?? '';
      form.iccid2 = row.iccid2 ?? '';
      form.phone = row.phone ?? '';
      form.phone2 = row.phone2 ?? '';
      form.telecomOperators = row.telecomOperators ?? '';
      form.telecomOperators2 = row.telecomOperators2 ?? '';
      form.deviceStatus = row.deviceStatus ?? null;
    } else {
      isEdit.value = false;
      editingId.value = '';
    }
    showModal.value = true;
  }

  const columns = computed<DataTableColumns<MmbaDevice>>(() => [
    { title: t('mmbaDevice.col.id'), key: 'id', ellipsis: { tooltip: true }, width: 120 },
    { title: t('mmbaDevice.col.staffName'), key: 'staffName', width: 100 },
    {
      title: t('mmbaDevice.col.userStatus'),
      key: 'enable',
      width: 96,
      render: (row) => formatUserEnable(row.enable ?? null),
    },
    { title: t('mmbaDevice.col.deviceId'), key: 'deviceId', ellipsis: { tooltip: true }, width: 140 },
    { title: t('mmbaDevice.col.deviceName'), key: 'deviceName', ellipsis: { tooltip: true }, width: 140 },
    { title: t('mmbaDevice.col.deviceType'), key: 'deviceType', ellipsis: { tooltip: true }, width: 120 },
    {
      title: t('mmbaDevice.col.phone'),
      key: 'phone',
      ellipsis: { tooltip: true },
      width: 180,
      render: (row) => formatMergedPhones(row),
    },
    {
      title: t('mmbaDevice.col.deviceStatus'),
      key: 'deviceStatus',
      width: 100,
      render: (row) => formatDeviceStatus(row.deviceStatus ?? null),
    },
    {
      title: t('mmbaDevice.col.loginStatus'),
      key: 'loginStatus',
      width: 88,
      render: (row) => formatLoginStatus(row.loginStatus ?? null),
    },
    { title: t('mmbaDevice.col.imei'), key: 'imei', ellipsis: { tooltip: true }, width: 150 },
    { title: t('mmbaDevice.col.iccid'), key: 'iccid', ellipsis: { tooltip: true }, width: 160 },
    {
      title: t('mmbaDevice.col.updateTime'),
      key: 'updateTime',
      width: 170,
      render: (row) => formatTime(row.updateTime),
    },
    {
      title: t('mmbaDevice.action'),
      key: 'actions',
      width: 90,
      fixed: 'right',
      render: (row) =>
        h(
          NButton,
          {
            size: 'small',
            type: 'primary',
            text: true,
            disabled: !hasAnyPermission(['MMBA_DEVICE:UPDATE']),
            onClick: () => openForm(row),
          },
          { default: () => t('mmbaDevice.edit') }
        ),
    },
  ]);

  async function load() {
    loading.value = true;
    try {
      const res = await getMmbaDevicePage({
        current: page.current,
        pageSize: page.pageSize,
        keyword: keyword.value || '',
      });
      tableData.value = res.list ?? [];
      total.value = res.total ?? 0;
    } finally {
      loading.value = false;
    }
  }

  function handleKeywordSearch() {
    page.current = 1;
    load();
  }

  function onPageSizeChange() {
    page.current = 1;
    load();
  }

  async function handleSubmit() {
    try {
      await formRef.value?.validate();
    } catch {
      return false;
    }
    try {
      if (isEdit.value) {
        await updateMmbaDevice({
          id: editingId.value,
          deviceId: form.deviceId.trim() || undefined,
          staffName: form.staffName.trim() || undefined,
          deviceName: form.deviceName || undefined,
          deviceType: form.deviceType || undefined,
          imei: form.imei || undefined,
          imei2: form.imei2 || undefined,
          iccid: form.iccid || undefined,
          iccid2: form.iccid2 || undefined,
          phone: form.phone || undefined,
          phone2: form.phone2 || undefined,
          telecomOperators: form.telecomOperators || undefined,
          telecomOperators2: form.telecomOperators2 || undefined,
          deviceStatus: form.deviceStatus ?? undefined,
        });
      } else {
        await addMmbaDevice({
          id: form.id.trim(),
          deviceId: form.deviceId.trim() || undefined,
          staffName: form.staffName.trim() || undefined,
          deviceName: form.deviceName || undefined,
          deviceType: form.deviceType || undefined,
          imei: form.imei || undefined,
          imei2: form.imei2 || undefined,
          iccid: form.iccid || undefined,
          iccid2: form.iccid2 || undefined,
          phone: form.phone || undefined,
          phone2: form.phone2 || undefined,
          telecomOperators: form.telecomOperators || undefined,
          telecomOperators2: form.telecomOperators2 || undefined,
          deviceStatus: form.deviceStatus ?? undefined,
        });
      }
      message.success(t('common.saveSuccess'));
      showModal.value = false;
      load();
      return true;
    } catch {
      return false;
    }
  }

  function onMmbaDeviceSyncSse() {
    if (route.name === SystemRouteEnum.SYSTEM_MMBA_DEVICE) {
      syncUiAwaitingRefresh.value = true;
    }
  }

  async function handleSyncOrRefreshClick() {
    if (syncUiAwaitingRefresh.value) {
      try {
        await load();
      } finally {
        syncUiAwaitingRefresh.value = false;
      }
      return;
    }
    const kw = keyword.value.trim();
    if (total.value === 0 && !kw) {
      message.warning(t('mmbaDevice.syncNeedDevices'));
      return;
    }
    openModal({
      type: 'warning',
      title: t('mmbaDevice.sync'),
      content: t('mmbaDevice.syncConfirm'),
      positiveText: t('common.confirm'),
      negativeText: t('common.cancel'),
      onPositiveClick: async () => {
        await syncMmbaDevices();
        message.info(t('mmbaDevice.syncSubmitted'));
      },
    });
  }

  async function handleImport({ file, onFinish, onError }: UploadCustomRequestOptions) {
    try {
      const res = await importMmbaDevice(file.file as File);
      onFinish();
      const ok = res.successCount ?? 0;
      const fail = res.failCount ?? 0;
      if (fail > 0) {
        message.warning(t('mmbaDevice.importPartial', { ok, fail }));
        const errs = res.errorMessages?.slice(0, 5) ?? [];
        errs.forEach((e) => {
          message.warning(t('mmbaDevice.importRowErr', { row: e.rowNum, msg: e.errMsg }));
        });
      } else {
        message.success(t('mmbaDevice.importOk', { ok }));
      }
      load();
    } catch {
      onError();
    }
  }

  onMounted(() => {
    window.addEventListener(MMBA_DEVICE_SYNC_DOM_EVENT, onMmbaDeviceSyncSse);
    load();
  });

  onUnmounted(() => {
    window.removeEventListener(MMBA_DEVICE_SYNC_DOM_EVENT, onMmbaDeviceSyncSse);
    syncUiAwaitingRefresh.value = false;
  });
</script>
