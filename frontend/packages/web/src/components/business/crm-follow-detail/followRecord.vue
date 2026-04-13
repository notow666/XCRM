<template>
  <div v-if="listData.length" class="crm-follow-record-list" :style="{ height: props.virtualScrollHeight }" @scroll="handleScroll">
    <div
      v-for="item in listData"
      :key="item[props.keyField]"
      class="crm-follow-record-item"
    >
      <div class="crm-follow-time-line-track">
        <div :class="`crm-follow-time-dot ${getFutureClass(item)}`"></div>
        <div class="crm-follow-time-line-bar"></div>
      </div>
      <div class="mb-[24px] flex w-full flex-col gap-[16px]">
        <div class="crm-follow-record-title h-[32px]">
          <div class="flex items-center gap-[16px]">
            <slot name="titleLeft" :item="item"></slot>
<!--            <StatusTagSelect
              v-if="item.status && props.type === 'followRecord'"
              v-model:status="item.status"
              :disabled="!props.getDisabledFun?.(item) || !!item.converted"
              @change="() => emit('change', item)"
            />-->
            <CrmTag v-if="item.status && item.converted"> {{ t('common.hasConvertToRecord') }} </CrmTag>
            <div class="text-[var(--text-n1)]">{{ getShowTime(item) }}</div>
            <div class="crm-follow-record-method">
              {{ (props.type === 'followRecord' ? item.followMethod : item.method) ?? '-' }}
            </div>
          </div>

          <slot name="headerAction" :item="item"></slot>
        </div>

        <div class="crm-follow-record-base-info">
          <CrmDetailCard :description="props.getDescriptionFun(item)">
            <template v-for="ele in props.getDescriptionFun(item)" :key="ele.key" #[ele.key]="{ item: descItem }">
              <template v-if="['customerName', 'clueName'].includes(ele.key) && !props.disabledOpenDetail">
                <slot name="customerName">
                  <CrmTableButton @click="goDetail(ele.key, item)">
                    {{ ele.value }}
                    <template #trigger> {{ ele.value }} </template>
                  </CrmTableButton>
                </slot>
              </template>
              <template v-else-if="ele.key === 'stageName'">
                <slot :name="ele.key" :desc-item="descItem" :item="item">
                  {{ getStageName(item) }}
                </slot>
              </template>
              <slot v-else :name="ele.key" :desc-item="descItem" :item="item"></slot>
            </template>
          </CrmDetailCard>
        </div>
        <div class="crm-follow-record-content" v-html="item.content.replace(/\n/g, '<br />')"></div>
      </div>
    </div>
  </div>
  <div v-else class="w-full p-[16px] text-center text-[var(--text-n4)]">
    {{ props.emptyText }}
  </div>
</template>

<script setup lang="ts">
  import dayjs from 'dayjs';

  import { CustomerFollowPlanStatusEnum } from '@lib/shared/enums/customerEnum';
  import { useI18n } from '@lib/shared/hooks/useI18n';
  import type { CustomerFollowPlanListItem, FollowDetailItem } from '@lib/shared/models/customer';

  import type { Description } from '@/components/pure/crm-detail-card/index.vue';
  import CrmDetailCard from '@/components/pure/crm-detail-card/index.vue';
  import CrmTableButton from '@/components/pure/crm-table-button/index.vue';
  import CrmTag from '@/components/pure/crm-tag/index.vue';
  import StatusTagSelect from './statusTagSelect.vue';

  import useOpenNewPage from '@/hooks/useOpenNewPage';

  import { ClueRouteEnum, CustomerRouteEnum } from '@/enums/routeEnum';

  const { t } = useI18n();
  const props = defineProps<{
    type: 'followRecord' | 'followPlan';
    keyField: string;
    getDescriptionFun: (item: FollowDetailItem) => Description[];
    getDisabledFun?: (item: FollowDetailItem) => boolean;
    virtualScrollHeight: string;
    emptyText?: string;
    disabledOpenDetail?: boolean;
  }>();

  const emit = defineEmits<{
    (e: 'reachBottom'): void;
    (e: 'change', item: FollowDetailItem): void;
  }>();

  const listData = defineModel<FollowDetailItem[]>('data', {
    default: [],
  });

  function handleScroll(e: Event) {
    const target = e.target as HTMLElement;
    if (target.scrollHeight - target.scrollTop - target.clientHeight < 50) {
      emit('reachBottom');
    }
  }

  function getFutureClass(item: FollowDetailItem) {
    if (props.type === 'followPlan') {
      const isNotFuture = [CustomerFollowPlanStatusEnum.CANCELLED, CustomerFollowPlanStatusEnum.COMPLETED].includes(
        (item as CustomerFollowPlanListItem).status
      );
      return isNotFuture ? '' : 'crm-follow-dot-future';
    }

    return new Date(item.followTime).getTime() > Date.now() ? 'crm-follow-dot-future' : '';
  }

  function getShowTime(item: FollowDetailItem) {
    const time = 'estimatedTime' in item ? item.estimatedTime : item.followTime;
    return time ? dayjs(time).format('YYYY-MM-DD') : '-';
  }

  function getStageName(item: FollowDetailItem) {
    // 跟进计划和跟进记录使用不同的字段
    if (props.type === 'followPlan') {
      const planItem = item as CustomerFollowPlanListItem;
      const stageStatus = planItem.nextStageStatus;
      const stageName = planItem.nextStageName;
      if (!stageName && !stageStatus) return '-';
      // FAILED 状态直接显示"无效客户"
      if (stageStatus === 'FAILED') return '无效客户';
      const prefixMap: Record<string, string> = {
        NEW: '待',
        IN_PROGRESS: '',
        COMPLETED: '已',
      };
      const suffixMap: Record<string, string> = {
        NEW: '',
        IN_PROGRESS: '中',
        COMPLETED: '',
      };
      const status = stageStatus || '';
      const prefix = status ? (prefixMap[status] || '') : '';
      const suffix = status ? (suffixMap[status] || '') : '';
      const name = stageName || '';
      return `${prefix}${name}${suffix}` || '-';
    }
    // 跟进记录保持原有逻辑
    if (!item.stageName && !item.stageStatus) return '-';
    // FAILED 状态直接显示"无效客户"
    if (item.stageStatus === 'FAILED') return '无效客户';
    const prefixMap: Record<string, string> = {
      NEW: '待',
      IN_PROGRESS: '',
      COMPLETED: '已',
    };
    const suffixMap: Record<string, string> = {
      NEW: '',
      IN_PROGRESS: '中',
      COMPLETED: '',
    };
    const status = item.stageStatus || '';
    const prefix = status ? (prefixMap[status] || '') : '';
    const suffix = status ? (suffixMap[status] || '') : '';
    const name = item.stageName || '';
    return `${prefix}${name}${suffix}` || '-';
  }

  const { openNewPage } = useOpenNewPage();
  function goDetail(key: string, item: FollowDetailItem) {
    if (key === 'clueName') {
      if (item.poolId) {
        openNewPage(ClueRouteEnum.CLUE_MANAGEMENT_POOL, {
          id: item.clueId,
          name: item.clueName,
          poolId: item.poolId,
        });
      } else {
        openNewPage(ClueRouteEnum.CLUE_MANAGEMENT, {
          id: item.clueId,
          transitionType: undefined,
          name: item.clueName,
        });
      }
    } else if (item.poolId) {
      openNewPage(CustomerRouteEnum.CUSTOMER_OPEN_SEA, {
        id: item.customerId,
        poolId: item.poolId,
      });
    } else {
      openNewPage(CustomerRouteEnum.CUSTOMER_INDEX, {
        id: item.customerId,
      });
    }
  }
</script>

<style scoped lang="less">
  .crm-follow-record-list {
    overflow-y: auto;
    height: 100%;
    box-sizing: border-box;
    padding: 0 0 24px 0;
  }
  .crm-follow-record-item {
    display: flex;
    gap: 16px;
    padding-bottom: 24px;
    .crm-follow-time-line-track {
      padding-top: 12px;
      width: 8px;
      display: flex;
      flex-direction: column;
      align-items: center;
      .crm-follow-time-dot {
        width: 8px;
        height: 8px;
        border: 2px solid var(--text-n7);
        border-radius: 50%;
        flex-shrink: 0;
        &.crm-follow-dot-future {
          border-color: var(--primary-8);
        }
      }
      .crm-follow-time-line-bar {
        width: 2px;
        min-height: 20px;
        background: var(--text-n8);
        flex: 1;
      }
    }
    .crm-follow-record-title {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      min-height: 32px;
      flex-wrap: wrap;
      .crm-follow-record-method {
        color: var(--text-n1);
        font-weight: 500;
      }
    }
    .crm-follow-record-base-info {
      flex: 1;
      min-width: 0;
    }
    .crm-follow-record-content {
      padding: 12px;
      border-radius: var(--border-radius-small);
      background: var(--text-n9);
      word-break: break-word;
    }
  }
</style>
