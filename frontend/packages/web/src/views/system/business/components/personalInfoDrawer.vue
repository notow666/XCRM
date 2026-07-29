<template>
  <CrmDrawer
    v-model:show="visible"
    width="100%"
    :title="t('system.personal.info.title')"
    :footer="false"
    :show-back="true"
    :closable="false"
    :body-content-class="bodyClass"
  >
    <n-scrollbar>
      <CrmCard no-content-padding hide-footer auto-height class="mb-[16px]">
        <CrmTab v-model:active-tab="activeTab" no-content :tab-list="tabList" type="line" @change="searchData()" />
      </CrmCard>
      <CrmCard v-if="activeTab === PersonalEnum.INFO" hide-footer :special-height="64">
        <div class="flex font-medium text-[var(--text-n1)]">
          <n-p>{{ t('common.baseInfo') }}</n-p>
        </div>
        <div class="flex w-full items-center gap-[8px] py-[16px]">
          <CrmAvatar />
          <div class="flex-1">
            <div class="text-[var(--text-n1)]">{{ personalInfo.userName }}</div>
            <div class="flex items-center gap-[4px]">
              <n-tag
                v-if="personalInfo.userId === 'admin'"
                :bordered="false"
                size="small"
                :color="{
                  color: 'var(--primary-6)',
                  textColor: 'var(--primary-8)',
                }"
              >
                {{ t('common.admin') }}
              </n-tag>
              <template v-else>
                <CrmTag
                  v-for="role in personalInfo.roles"
                  :key="role.id"
                  :bordered="false"
                  size="small"
                  :color="{
                    color: 'var(--primary-6)',
                    textColor: 'var(--primary-8)',
                  }"
                >
                  {{ role.name }}
                </CrmTag>
              </template>
            </div>
          </div>
        </div>
        <div
          class="grid w-full grid-cols-3 gap-[8px] rounded-[var(--border-radius-small)] bg-[var(--text-n9)] p-[24px]"
        >
          <div class="flex">
            <n-p class="m-[0] text-[var(--text-n4)]">{{ t('system.personal.phone') }}</n-p>
            <n-p class="mx-[8px] my-[0] text-[var(--text-n1)]">{{ personalInfo.phone }}</n-p>
          </div>
          <div class="flex">
            <n-p class="m-[0] text-[var(--text-n4)]">{{ t('system.personal.email') }}</n-p>
            <n-p class="mx-[8px] my-[0] text-[var(--text-n1)]">{{ personalInfo.email }}</n-p>
          </div>
          <div v-if="!userStore.isAdmin" class="flex">
            <n-p class="m-[0] text-[var(--text-n4)]">{{ t('system.personal.department') }}</n-p>
            <n-p class="mx-[8px] my-[0] text-[var(--text-n1)]">{{ personalInfo.departmentName }}</n-p>
          </div>
        </div>
        <div class="py-[24px]">
          <n-button @click="changePassword">
            {{ t('system.personal.changePassword') }}
          </n-button>
        </div>
      </CrmCard>
      <CrmCard v-if="activeTab === PersonalEnum.MY_PLAN" no-content-padding hide-footer :special-height="64">
        <FollowDetail
          :show-add="false"
          :refresh-key="refreshKey"
          active-type="followPlan"
          wrapper-class="h-[calc(100vh-155px)]"
          virtual-scroll-height="calc(100vh - 252px)"
          follow-api-key="myPlan"
          source-id="NULL"
          :any-permission="['CUSTOMER_MANAGEMENT:READ', 'OPPORTUNITY_MANAGEMENT:READ', 'CLUE_MANAGEMENT:READ']"
        />
      </CrmCard>
      <CrmCard v-if="activeTab === PersonalEnum.MY_WECHAT" hide-footer :special-height="64">
        <n-spin :show="wechatLoading" :description="wechatLoadingText" class="min-h-[240px]">
          <div v-if="personalWechat.bound && personalWechat.wechats.length" class="grid gap-[16px]">
            <div
              v-for="wechat in personalWechat.wechats"
              :key="wechat.wxId || wechat.wxPhone || wechat.wxNickName"
              class="rounded-[var(--border-radius-small)] bg-[var(--text-n9)] p-[24px]"
            >
              <div class="mb-[16px] text-[16px] font-medium text-[var(--text-n1)]">
                {{ formatValue(wechat.wxNickName) }}
              </div>
              <div class="grid gap-[12px] md:grid-cols-2 xl:grid-cols-3">
                <div v-for="field in wechatFieldList" :key="field.key" class="flex">
                  <n-p class="m-[0] shrink-0 text-[var(--text-n4)]">{{ field.label }}</n-p>
                  <n-p class="mx-[8px] my-[0] break-all text-[var(--text-n1)]">
                    {{ formatWechatValue(field.key, wechat[field.key]) }}
                  </n-p>
                </div>
                <div class="flex">
                  <n-p class="m-[0] shrink-0 text-[var(--text-n4)]">{{ updateTimeLabel }}</n-p>
                  <n-p class="mx-[8px] my-[0] text-[var(--text-n1)]">{{ formatTime(wechat.updateTime) }}</n-p>
                </div>
              </div>
            </div>
          </div>
          <n-empty v-else :description="wechatEmptyText" class="py-[64px]" />
        </n-spin>
      </CrmCard>
      <DialPreference v-if="activeTab === PersonalEnum.DIAL_SETTING" />
      <apiKey v-if="activeTab === PersonalEnum.API_KEY" />
    </n-scrollbar>
  </CrmDrawer>
  <EditPasswordModal v-model:show="showEditPasswordModal" @init-sync="searchData()" />
</template>

<script setup lang="ts">
  import { ref } from 'vue';
  import { NButton, NEmpty, NP, NScrollbar, NSpin, NTag, TabPaneProps } from 'naive-ui';
  import axios from 'axios';
  import dayjs from 'dayjs';

  import { PersonalEnum } from '@lib/shared/enums/systemEnum';
  import { useI18n } from '@lib/shared/hooks/useI18n';
  import { PersonalWechatResponse } from '@lib/shared/models/system/business';
  import { OrgUserInfo } from '@lib/shared/models/system/org';

  import CrmCard from '@/components/pure/crm-card/index.vue';
  import CrmDrawer from '@/components/pure/crm-drawer/index.vue';
  import CrmTab from '@/components/pure/crm-tab/index.vue';
  import CrmTag from '@/components/pure/crm-tag/index.vue';
  import CrmAvatar from '@/components/business/crm-avatar/index.vue';
  import FollowDetail from '@/components/business/crm-follow-detail/index.vue';
  import apiKey from './apiKey.vue';
  import DialPreference from './dialPreference.vue';
  import EditPasswordModal from '@/views/system/business/components/editPasswordModal.vue';

  import { getPersonalInfo, getPersonalWechat } from '@/api/modules';
  import { defaultUserInfo } from '@/config/business';
  import { useUserStore } from '@/store';
  import { hasAnyPermission } from '@/utils/permission';

  const { t } = useI18n();
  const userStore = useUserStore();

  const visible = defineModel<boolean>('visible', {
    required: true,
  });

  const activeTab = defineModel<PersonalEnum>('activeTabValue', {
    required: false,
    default: PersonalEnum.INFO,
  });

  const personalInfo = ref<OrgUserInfo>({
    ...defaultUserInfo,
  });

  const personalWechat = ref<PersonalWechatResponse>({
    bound: false,
    wechats: [],
  });
  const wechatLoading = ref(false);

  const bodyClass = ref<string>('crm-drawer-content');

  const showEditPasswordModal = ref<boolean>(false);
  const refreshKey = ref(0);

  const myWechatTabLabel = '\u6211\u7684\u5fae\u4fe1';
  const wechatLoadingText = '\u6b63\u5728\u67e5\u8be2\u4e2d';
  const wechatEmptyText = '\u60a8\u6682\u65f6\u672a\u7ed1\u5b9a,\u8bf7\u8054\u7cfb\u7ba1\u7406\u5458';
  const updateTimeLabel = '\u66f4\u65b0\u65f6\u95f4';

  const tabList = computed<TabPaneProps[]>(() => {
    return [
      {
        name: PersonalEnum.INFO,
        tab: t('system.personal.info'),
      },
      {
        name: PersonalEnum.MY_PLAN,
        tab: t('system.personal.plan'),
      },
      {
        name: PersonalEnum.MY_WECHAT,
        tab: myWechatTabLabel,
      },
      {
        name: PersonalEnum.DIAL_SETTING,
        tab: t('system.personal.dialSetting'),
      },
      ...(hasAnyPermission(['PERSONAL_API_KEY:READ'])
        ? [
            {
              name: PersonalEnum.API_KEY,
              tab: t('system.personal.apiKey'),
            },
          ]
        : []),
    ];
  });

  const wechatFieldList: { label: string; key: keyof PersonalWechatResponse['wechats'][number] }[] = [
    { label: '\u5fae\u4fe1\u8d26\u53f7\u6635\u79f0', key: 'wxNickName' },
    { label: '\u5fae\u4fe1\u8d26\u53f7id', key: 'wxId' },
    { label: 'wx\u5e10\u53f7', key: 'wxAccount' },
    { label: '\u5fae\u4fe1\u7ed1\u5b9a\u7684\u624b\u673a\u53f7', key: 'wxPhone' },
    { label: '\u6d3b\u8dc3\u72b6\u6001', key: 'mappingStatus' },
  ];

  async function searchData() {
    if (activeTab.value === PersonalEnum.INFO) {
      try {
        personalInfo.value = await getPersonalInfo();
      } catch (error: any) {
        if (axios.isCancel(error) || error?.name === 'CanceledError' || error?.code === 'ERR_CANCELED') {
          return;
        }
        throw error;
      }
      return;
    }
    if (activeTab.value === PersonalEnum.MY_WECHAT) {
      wechatLoading.value = true;
      try {
        personalWechat.value = await getPersonalWechat();
      } finally {
        wechatLoading.value = false;
      }
    }
  }

  function changePassword() {
    showEditPasswordModal.value = true;
  }

  function formatValue(value?: string | number | null) {
    return value || '-';
  }

  function formatWechatValue(field: keyof PersonalWechatResponse['wechats'][number], value?: string | number | null) {
    if (field === 'mappingStatus') {
      if (value === 'ACTIVE') {
        return '\u6d3b\u8dc3';
      }
      if (value === 'INACTIVE') {
        return '\u79bb\u7ebf';
      }
    }
    return formatValue(value);
  }

  function formatTime(value?: number | null) {
    return value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-';
  }

  watch(
    () => visible.value,
    (val) => {
      if (val) {
        searchData();
      }
    }
  );

  watch(
    () => activeTab.value,
    (val) => {
      if (val === PersonalEnum.INFO) {
        searchData();
      }
    }
  );
</script>

<style scoped lang="less"></style>
