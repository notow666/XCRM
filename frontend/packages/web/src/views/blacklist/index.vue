<template>
  <CrmCard hide-footer no-content-padding :special-height="licenseStore.expiredDuring ? 64 : 0">
    <div class="mb-[16px] flex flex-wrap items-center justify-between gap-[12px] px-[16px] pt-[16px]">
      <div class="flex shrink-0 flex-wrap items-center gap-[12px]">
        <n-button v-if="can('ADD')" type="primary" :disabled="busy" @click="openAdd">新建</n-button>
        <n-button v-if="can('IMPORT')" :disabled="busy" @click="openImport">导入</n-button>
        <n-button v-if="can('EXPORT')" :loading="exporting" :disabled="busy" @click="handleExport">
          {{ selected.length ? `导出选中（${selected.length}）` : '导出筛选结果' }}
        </n-button>
        <n-button
          v-if="can('DELETE') && can('READ')"
          :disabled="busy || !selected.length"
          @click="confirmDelete(selected)"
        >
          批量删除
        </n-button>
        <n-button
          v-if="can('DELETE') && can('READ')"
          :disabled="busy || loading || !total"
          @click="confirmDeleteByCondition"
        >
          按筛选删除
        </n-button>
      </div>
      <div v-if="can('READ')" class="flex min-w-0 flex-1 flex-wrap items-center justify-end gap-[12px]">
        <n-input
          v-model:value="keyword"
          class="!w-[240px]"
          clearable
          placeholder="请输入手机号码或客户姓名"
          @keyup.enter="search"
        />
        <n-button type="primary" :loading="loading" @click="search">查询</n-button>
        <n-button @click="resetSearch">重置</n-button>
      </div>
    </div>
    <template v-if="can('READ')">
      <n-data-table
        v-model:checked-row-keys="selected"
        :columns="columns"
        :data="rows"
        :row-key="rowKey"
        :loading="loading"
        :scroll-x="600"
        :bordered="false"
        class="px-[16px]"
      />
      <div class="flex justify-end px-[16px] py-[16px]">
        <n-pagination v-model:page="page" :item-count="total" :page-size="20" @update:page="load" />
      </div>
    </template>
    <n-empty v-else class="py-[32px]" description="未开通查看权限，可使用已授权的操作" />
  </CrmCard>

  <CrmModal
    v-model:show="showAdd"
    size="small"
    title="新建黑名单"
    positive-text="保存"
    :ok-loading="busy"
    :cancel-button-props="{ disabled: busy }"
    :mask-closable="!busy"
    :closable="!busy"
    @confirm="save"
  >
    <n-form ref="formRef" :model="form" :rules="rules" label-placement="top">
      <n-form-item label="手机号码" path="mobile">
        <n-input v-model:value="form.mobile" :maxlength="11" placeholder="请输入11位手机号" :disabled="busy" />
      </n-form-item>
      <n-form-item label="客户姓名" path="customerName">
        <n-input v-model:value="form.customerName" :maxlength="255" placeholder="请输入客户姓名" :disabled="busy" />
      </n-form-item>
    </n-form>
  </CrmModal>

  <CrmModal
    v-model:show="showImport"
    size="medium"
    title="导入黑名单"
    :positive-text="lastImport ? '完成' : '开始导入'"
    :ok-loading="busy"
    :ok-button-props="{ disabled: (!lastImport && !fileList.length) || busy }"
    :cancel-button-props="{ disabled: busy }"
    :mask-closable="!busy"
    :closable="!busy"
    @confirm="handleImport"
  >
    <n-alert type="default" class="mb-[16px]">
      <div class="flex flex-wrap items-center gap-[16px]">
        <span>请按模板填写，手机号码必填，客户姓名选填。</span>
        <n-button text type="primary" @click="download()">下载导入模板</n-button>
      </div>
      <div class="mt-[8px]">单次最多导入10000行，正确行继续导入，错误行可下载查看。</div>
    </n-alert>
    <CrmUpload
      v-model:file-list="fileList"
      accept="excel"
      :max-size="10"
      size-unit="MB"
      directory-dnd
      :disabled="busy"
      @change="lastImport = undefined"
    />
    <n-alert v-if="lastImport" class="mt-[16px]" :type="lastImport.failCount ? 'warning' : 'success'">
      成功导入 {{ lastImport.successCount }} 条，失败 {{ lastImport.failCount }} 行。
      <span v-if="lastImport.errorFileId">错误文件保留原表，红色行的首列批注中可查看错误原因。</span>
      <n-button
        v-if="lastImport.errorFileId"
        text
        type="primary"
        @click="download(lastImport.errorFileId, lastImport.errorFileName)"
        >下载错误文件</n-button
      >
    </n-alert>
  </CrmModal>
</template>

<script setup lang="ts">
  import { computed, h, onMounted, reactive, ref } from 'vue';
  import {
    type DataTableColumns,
    type FormInst,
    type FormRules,
    NAlert,
    NButton,
    NDataTable,
    NEmpty,
    NForm,
    NFormItem,
    NInput,
    NPagination,
    useDialog,
    useMessage,
  } from 'naive-ui';

  import CrmCard from '@/components/pure/crm-card/index.vue';
  import CrmModal from '@/components/pure/crm-modal/index.vue';
  import CrmUpload from '@/components/pure/crm-upload/index.vue';
  import type { CrmFileItem } from '@/components/pure/crm-upload/types';

  import type { BlacklistResult, BlacklistRow } from '@/api/modules/blacklist';
  import {
    addBlacklist,
    blacklistPage,
    deleteBlacklist,
    deleteBlacklistByCondition,
    downloadBlacklistFile,
    exportBlacklist,
    importBlacklist,
  } from '@/api/modules/blacklist';
  import useLicenseStore from '@/store/modules/setting/license';
  import { hasPermission } from '@/utils/permission';

  const licenseStore = useLicenseStore();
  const message = useMessage();
  const dialog = useDialog();
  const can = (action: string) => hasPermission(`BLACKLIST:${action}`);
  const rows = ref<BlacklistRow[]>([]);
  const selected = ref<string[]>([]);
  const keyword = ref('');
  const appliedKeyword = ref('');
  const page = ref(1);
  const total = ref(0);
  const loading = ref(false);
  const busy = ref(false);
  const exporting = ref(false);
  const showAdd = ref(false);
  const showImport = ref(false);
  const formRef = ref<FormInst>();
  const form = reactive({ mobile: '', customerName: '' });
  const fileList = ref<CrmFileItem[]>([]);
  const lastImport = ref<BlacklistResult>();
  let requestSequence = 0;
  const rowKey = (row: BlacklistRow) => row.id;
  const rules: FormRules = {
    mobile: [{ required: true, pattern: /^1[0-9]\d{9}$/, message: '请输入11位有效手机号', trigger: ['input', 'blur'] }],
  };
  async function load() {
    if (!can('READ')) return;
    const sequence = ++requestSequence;
    loading.value = true;
    try {
      const queryKeyword = keyword.value.trim();
      const result = await blacklistPage(queryKeyword, page.value, 20);
      if (sequence !== requestSequence) return;
      appliedKeyword.value = queryKeyword;
      rows.value = result.list;
      total.value = result.total;
      selected.value = [];
    } finally {
      if (sequence === requestSequence) loading.value = false;
    }
  }
  function search() {
    page.value = 1;
    load();
  }
  function resetSearch() {
    keyword.value = '';
    search();
  }
  function openAdd() {
    form.mobile = '';
    form.customerName = '';
    showAdd.value = true;
  }
  async function save() {
    if (busy.value) return;
    await formRef.value?.validate();
    busy.value = true;
    try {
      await addBlacklist({ ...form });
      showAdd.value = false;
      message.success('保存成功');
      await load();
    } finally {
      busy.value = false;
    }
  }
  function confirmDelete(ids: string[]) {
    dialog.warning({
      title: '删除黑名单',
      content: `确认删除所选 ${ids.length} 条数据？删除后立即解除黑名单限制。`,
      positiveText: '删除',
      negativeText: '取消',
      onPositiveClick: async () => {
        busy.value = true;
        try {
          const result = await deleteBlacklist(ids);
          message.success(`成功删除 ${result.successCount} 条`);
          page.value = 1;
          await load();
        } finally {
          busy.value = false;
        }
      },
    });
  }
  function confirmDeleteByCondition() {
    const filter = appliedKeyword.value;
    dialog.warning({
      title: '按筛选删除',
      content: filter
        ? `确认删除当前筛选条件下所有页的黑名单数据（当前共 ${total.value} 条）？删除后立即解除限制。`
        : `当前未设置筛选条件，确认删除全部黑名单数据（当前共 ${total.value} 条）？删除后立即解除限制。`,
      positiveText: '删除',
      negativeText: '取消',
      onPositiveClick: async () => {
        if (busy.value) return;
        busy.value = true;
        try {
          const result = await deleteBlacklistByCondition(filter);
          message.success(`成功删除 ${result.successCount} 条`);
          page.value = 1;
          await load();
        } finally {
          busy.value = false;
        }
      },
    });
  }
  const columns = computed<DataTableColumns<BlacklistRow>>(() => {
    const result: DataTableColumns<BlacklistRow> = [];
    if (can('DELETE') || can('EXPORT')) result.push({ type: 'selection' });
    result.push(
      { title: '手机号码', key: 'mobile' },
      { title: '客户姓名', key: 'customerName', render: (row) => row.customerName || '—' }
    );
    if (can('DELETE'))
      result.push({
        title: '操作',
        key: 'actions',
        width: 100,
        render: (row) =>
          h(
            NButton,
            { text: true, type: 'error', disabled: busy.value, onClick: () => confirmDelete([row.id]) },
            { default: () => '删除' }
          ),
      });
    return result;
  });

  function openImport() {
    fileList.value = [];
    lastImport.value = undefined;
    showImport.value = true;
  }
  async function handleImport() {
    if (busy.value) return;
    if (lastImport.value) {
      showImport.value = false;
      fileList.value = [];
      lastImport.value = undefined;
      return;
    }
    const file = fileList.value[0]?.file;
    if (!file || busy.value) return;
    busy.value = true;
    try {
      const result = await importBlacklist(file);
      lastImport.value = result;
      if (!result.failCount) {
        showImport.value = false;
        fileList.value = [];
        lastImport.value = undefined;
        message.success(`成功导入 ${result.successCount} 条`);
      }
      await load();
    } finally {
      busy.value = false;
    }
  }
  function saveBlob(blob: Blob, name: string) {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = name;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
  async function handleExport() {
    if (busy.value) return;
    exporting.value = true;
    busy.value = true;
    try {
      const blob = await exportBlacklist(keyword.value, selected.value);
      saveBlob(blob, '黑名单.xlsx');
    } finally {
      exporting.value = false;
      busy.value = false;
    }
  }
  async function download(id?: string, name?: string) {
    const blob = await downloadBlacklistFile(id);
    saveBlob(blob, name || (id ? '黑名单导入错误.xlsx' : '黑名单导入模板.xlsx'));
  }
  onMounted(() => {
    load();
  });
</script>
