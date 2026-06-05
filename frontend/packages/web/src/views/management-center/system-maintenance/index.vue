<template>
  <CrmCard no-content-padding hide-footer>
    <div class="space-y-4 p-4">
      <div class="text-base font-semibold text-[var(--text-n1)]">
        {{ t('menu.managementCenter.systemMaintenance') }}
      </div>

      <n-spin :show="loading">
        <div class="mb-4 grid gap-4 lg:grid-cols-2 xl:grid-cols-[2fr_1.5fr_1.5fr]">
          <!-- 发布系统公告 -->
          <div
            class="flex flex-col rounded border border-[var(--text-n8)] bg-[var(--text-n10)] p-4 lg:col-span-2 xl:col-span-1"
          >
            <div class="mb-3 text-sm font-medium text-[var(--text-n2)]">
              {{ t('managementCenter.systemMaintenance.announcementTitle') }}
            </div>
            <NSpace vertical :size="12" class="flex-1">
              <NInput
                v-model:value="announcementForm.subject"
                :placeholder="t('managementCenter.systemMaintenance.announcementSubject')"
              />
              <NInput
                v-model:value="announcementForm.content"
                type="textarea"
                :rows="4"
                :placeholder="t('managementCenter.systemMaintenance.announcementContent')"
              />
              <div>
                <NPopconfirm @positive-click="handlePublishAnnouncement">
                  <template #trigger>
                    <NButton type="primary" :loading="publishing">
                      {{ t('managementCenter.systemMaintenance.publishAnnouncement') }}
                    </NButton>
                  </template>
                  {{ t('managementCenter.systemMaintenance.publishConfirm') }}
                </NPopconfirm>
              </div>
            </NSpace>
          </div>

          <!-- 强制全员下线 -->
          <div class="flex flex-col rounded border border-[var(--text-n8)] bg-[var(--text-n10)] p-4">
            <div class="mb-2 text-sm font-medium text-[var(--text-n2)]">
              {{ t('managementCenter.systemMaintenance.forceLogoutTitle') }}
            </div>
            <div class="mb-4 text-xs leading-relaxed text-[var(--text-n4)]">
              {{ t('managementCenter.systemMaintenance.forceLogoutHint', { seconds: graceSeconds }) }}
            </div>
            <div class="mb-4 grid grid-cols-2 gap-2">
              <div class="rounded bg-[var(--text-n9)] px-3 py-2">
                <div class="text-xs text-[var(--text-n4)]">
                  {{ t('managementCenter.systemMaintenance.onlineTenantUsers') }}
                </div>
                <div class="mt-1 text-xl font-semibold tabular-nums text-[var(--text-n1)]">
                  {{ status?.onlineTenantUserTotal ?? 0 }}
                </div>
              </div>
              <div class="rounded bg-[var(--text-n9)] px-3 py-2">
                <div class="text-xs text-[var(--text-n4)]">
                  {{ t('managementCenter.systemMaintenance.onlineDataSpecialistUsers') }}
                </div>
                <div class="mt-1 text-xl font-semibold tabular-nums text-[var(--text-n1)]">
                  {{ status?.onlineDataSpecialistUserCount ?? 0 }}
                </div>
              </div>
            </div>
            <div class="mt-auto pt-1">
              <NPopconfirm :disabled="forceLogoutPending" @positive-click="handleForceLogout">
                <template #trigger>
                  <NButton
                    type="error"
                    :loading="forceLogoutLoading || forceLogoutPending"
                    :disabled="forceLogoutPending"
                  >
                    {{ t('managementCenter.systemMaintenance.forceLogoutTitle') }}
                  </NButton>
                </template>
                {{ t('managementCenter.systemMaintenance.forceLogoutConfirm') }}
              </NPopconfirm>
            </div>
          </div>

          <!-- 系统维护模式 -->
          <div class="flex flex-col rounded border border-[var(--text-n8)] bg-[var(--text-n10)] p-4">
            <div class="mb-2 flex flex-wrap items-center gap-2">
              <span class="text-sm font-medium text-[var(--text-n2)]">
                {{ t('managementCenter.systemMaintenance.maintenanceTitle') }}
              </span>
              <NTag :type="status?.maintenanceMode ? 'warning' : 'success'" size="small">
                {{
                  status?.maintenanceMode
                    ? t('managementCenter.systemMaintenance.maintenanceActive')
                    : t('managementCenter.systemMaintenance.maintenanceNormal')
                }}
              </NTag>
            </div>
            <div class="mb-4 flex-1 text-xs leading-relaxed text-[var(--text-n4)]">
              {{ t('managementCenter.systemMaintenance.maintenanceHint') }}
            </div>
            <NSpace class="mt-auto pt-1">
              <NPopconfirm :disabled="status?.maintenanceMode" @positive-click="handleEnterMaintenance">
                <template #trigger>
                  <NButton type="warning" :disabled="status?.maintenanceMode" :loading="maintenanceLoading">
                    {{ t('managementCenter.systemMaintenance.enterMaintenance') }}
                  </NButton>
                </template>
                {{ t('managementCenter.systemMaintenance.enterConfirm') }}
              </NPopconfirm>
              <NPopconfirm :disabled="!status?.maintenanceMode" @positive-click="handleExitMaintenance">
                <template #trigger>
                  <NButton :disabled="!status?.maintenanceMode" :loading="maintenanceLoading">
                    {{ t('managementCenter.systemMaintenance.exitMaintenance') }}
                  </NButton>
                </template>
                {{ t('managementCenter.systemMaintenance.exitConfirm') }}
              </NPopconfirm>
            </NSpace>
          </div>
        </div>

        <!-- 最近公告 -->
        <div class="text-sm font-medium text-[var(--text-n2)]">
          {{ t('managementCenter.systemMaintenance.recentAnnouncements') }}
        </div>
        <NDataTable
          class="mt-2"
          :columns="announcementColumns"
          :data="announcementList"
          :loading="announcementLoading"
          :pagination="announcementPagination"
          remote
          @update:page="handleAnnouncementPageChange"
        />
      </n-spin>
    </div>
  </CrmCard>
</template>

<script setup lang="ts">
  import { computed, h, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
  import { NButton, NDataTable, NInput, NPopconfirm, NSpace, NSpin, NTag, useMessage } from 'naive-ui';
  import dayjs from 'dayjs';

  import { PLATFORM_FORCE_LOGOUT_DONE_DOM_EVENT } from '@lib/shared/constants/sseEventType';
  import { useI18n } from '@lib/shared/hooks/useI18n';

  import CrmCard from '@/components/pure/crm-card/index.vue';

  import {
    enterPlatformMaintenanceMode,
    exitPlatformMaintenanceMode,
    forcePlatformLogoutAll,
    getPlatformSystemMaintenanceStatus,
    pagePlatformSystemAnnouncements,
    type PlatformSystemAnnouncementItem,
    type PlatformSystemMaintenanceStatus,
    publishPlatformSystemAnnouncement,
  } from '@/api/modules';

  import type { DataTableColumns, PaginationProps } from 'naive-ui';

  const { t } = useI18n();
  const message = useMessage();

  const ANNOUNCEMENT_PAGE_SIZE = 5;

  const loading = ref(false);
  const publishing = ref(false);
  const forceLogoutLoading = ref(false);
  const forceLogoutPending = ref(false);
  const maintenanceLoading = ref(false);
  const announcementLoading = ref(false);
  const status = ref<PlatformSystemMaintenanceStatus | null>(null);
  const announcementList = ref<PlatformSystemAnnouncementItem[]>([]);
  const announcementPage = ref(1);
  const announcementTotal = ref(0);
  const graceSeconds = 60;

  const announcementForm = reactive({
    subject: '',
    content: '',
  });

  function formatCreateTime(value?: number) {
    if (!value) {
      return '-';
    }
    return dayjs(value).format('YYYY-MM-DD HH:mm:ss');
  }

  const announcementPagination = computed<PaginationProps>(() => ({
    page: announcementPage.value,
    pageSize: ANNOUNCEMENT_PAGE_SIZE,
    itemCount: announcementTotal.value,
    showSizePicker: false,
  }));

  const announcementColumns = computed<DataTableColumns<PlatformSystemAnnouncementItem>>(() => [
    { title: t('managementCenter.systemMaintenance.announcementSubject'), key: 'subject', ellipsis: { tooltip: true } },
    {
      title: t('managementCenter.systemMaintenance.announcementContent'),
      key: 'content',
      ellipsis: { tooltip: true },
    },
    { title: t('managementCenter.systemMaintenance.announcementOperator'), key: 'operatorId', width: 120 },
    {
      title: t('managementCenter.systemMaintenance.announcementCreateTime'),
      key: 'createTime',
      width: 170,
      render: (row) => h('span', formatCreateTime(row.createTime)),
    },
  ]);

  async function loadAnnouncements(page = announcementPage.value) {
    announcementLoading.value = true;
    try {
      const res = await pagePlatformSystemAnnouncements({
        current: page,
        pageSize: ANNOUNCEMENT_PAGE_SIZE,
      });
      announcementList.value = res.list ?? [];
      announcementTotal.value = res.total ?? 0;
      announcementPage.value = res.current ?? page;
    } finally {
      announcementLoading.value = false;
    }
  }

  async function loadStatus() {
    loading.value = true;
    try {
      status.value = await getPlatformSystemMaintenanceStatus();
    } finally {
      loading.value = false;
    }
  }

  function handleAnnouncementPageChange(page: number) {
    loadAnnouncements(page);
  }

  async function handlePublishAnnouncement() {
    if (!announcementForm.subject.trim()) {
      message.warning(t('managementCenter.systemMaintenance.subjectRequired'));
      return false;
    }
    if (!announcementForm.content.trim()) {
      message.warning(t('managementCenter.systemMaintenance.contentRequired'));
      return false;
    }
    publishing.value = true;
    try {
      await publishPlatformSystemAnnouncement({
        subject: announcementForm.subject.trim(),
        content: announcementForm.content.trim(),
      });
      message.success(t('managementCenter.systemMaintenance.publishSuccess'));
      announcementForm.subject = '';
      announcementForm.content = '';
      announcementPage.value = 1;
      await loadAnnouncements(1);
    } finally {
      publishing.value = false;
    }
    return true;
  }

  async function handleForceLogout() {
    forceLogoutLoading.value = true;
    try {
      await forcePlatformLogoutAll({ graceSeconds });
      message.success(t('managementCenter.systemMaintenance.forceLogoutSuccess'));
      forceLogoutPending.value = true;
    } finally {
      forceLogoutLoading.value = false;
    }
    return true;
  }

  function handleForceLogoutDone(event: Event) {
    forceLogoutPending.value = false;
    const detail = (event as CustomEvent).detail as {
      targetCount?: number;
      kickedCount?: number;
      failedCount?: number;
      maintenanceMode?: boolean;
      onlineUserTotal?: number;
      onlineTenantUserTotal?: number;
      onlineDataSpecialistUserCount?: number;
    };
    if (status.value && detail) {
      status.value = {
        ...status.value,
        maintenanceMode: detail.maintenanceMode ?? status.value.maintenanceMode,
        onlineUserTotal: detail.onlineUserTotal ?? status.value.onlineUserTotal,
        onlineTenantUserTotal: detail.onlineTenantUserTotal ?? status.value.onlineTenantUserTotal,
        onlineDataSpecialistUserCount:
          detail.onlineDataSpecialistUserCount ?? status.value.onlineDataSpecialistUserCount,
      };
    }
    message.success(
      t('managementCenter.systemMaintenance.forceLogoutDone', {
        kicked: detail?.kickedCount ?? 0,
        failed: detail?.failedCount ?? 0,
      })
    );
  }

  async function handleEnterMaintenance() {
    maintenanceLoading.value = true;
    try {
      await enterPlatformMaintenanceMode();
      message.success(t('managementCenter.systemMaintenance.enterSuccess'));
      await loadStatus();
    } finally {
      maintenanceLoading.value = false;
    }
    return true;
  }

  async function handleExitMaintenance() {
    maintenanceLoading.value = true;
    try {
      await exitPlatformMaintenanceMode();
      message.success(t('managementCenter.systemMaintenance.exitSuccess'));
      await loadStatus();
    } finally {
      maintenanceLoading.value = false;
    }
    return true;
  }

  onMounted(() => {
    loadStatus();
    loadAnnouncements(1);
    window.addEventListener(PLATFORM_FORCE_LOGOUT_DONE_DOM_EVENT, handleForceLogoutDone);
  });

  onBeforeUnmount(() => {
    window.removeEventListener(PLATFORM_FORCE_LOGOUT_DONE_DOM_EVENT, handleForceLogoutDone);
  });
</script>
