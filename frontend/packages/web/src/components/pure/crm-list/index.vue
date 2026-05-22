<template>
  <n-spin :show="props.loading" class="h-full min-h-0" content-class="h-full min-h-0 flex flex-col">
    <n-virtual-list
      ref="listRef"
      :class="['crm-list min-h-0 flex-1', containerStatusClass, props.class]"
      :item-size="props.itemHeight"
      :items="listData"
      :style="{
        height: props.virtualScrollHeight,
      }"
      :item-resizable="props.itemResizable"
      @scroll="handleReachBottomScroll"
      @wheel="handleWheel"
    >
      <template #default="{ item }">
        <slot name="item" :item="item">
          <div
            :key="item[props.keyField]"
            :class="[
              'crm-list-item',
              props.noHover ? 'crm-list-item--no-hover' : '',
              props.itemBorder ? 'crm-list-item--bordered' : '',
              props.itemClass,
              innerFocusItemKey === item[keyField] ? 'crm-list-item--focus' : '',
              innerActiveItemKey === item[keyField] ? props.activeItemClass : '',
            ]"
            :style="{
              height: `${props.itemHeight}px`,
            }"
          >
            <div class="flex-1 overflow-x-hidden" @click="emit('itemClick', item)">
              <slot name="title" :item="item"></slot>
            </div>
            <div class="flex items-center gap-[4px]">
              <div
                v-if="
                  $slots['itemAction'] || (props.itemMoreActions && props.itemMoreActions.length > 0 && !props.disabled)
                "
                class="crm-list-item-actions"
              >
                <slot name="itemAction" :item="item"></slot>
                <CrmMoreAction
                  v-if="props.itemMoreActions && props.itemMoreActions.length > 0"
                  :options="props.itemMoreActions"
                  trigger="click"
                  @select="handleMoreActionSelect($event, item)"
                  @close="handleMoreActionClose"
                  @click="handleClickMore(item)"
                >
                </CrmMoreAction>
              </div>
              <slot name="itemRight" :item="item"></slot>
            </div>
          </div>
        </slot>
      </template>
    </n-virtual-list>
  </n-spin>
</template>

<script setup lang="ts">
  import { NSpin, NVirtualList } from 'naive-ui';
  import { useDraggable } from 'vue-draggable-plus';

  import CrmMoreAction from '@/components/pure/crm-more-action/index.vue';
  import type { ActionsItem } from '@/components/pure/crm-more-action/type';

  import useContainerShadow from '@/hooks/useContainerShadow';

  const props = withDefaults(
    defineProps<{
      mode?: 'static' | 'remote'; // 静态数据或者远程数据
      bordered?: boolean; // 是否显示边框
      activeItemKey?: string | number; // 当前选中的项的 key
      focusItemKey?: string | number; // 聚焦的项的 key
      itemMoreActions?: ActionsItem[]; // 节点展示在省略号按钮内的更多操作
      keyField?: string; // 唯一值 key 的字段名，默认为 key
      itemHeight?: number; // 每一项的高度
      emptyText?: string; // 空数据时的文案
      noMoreData?: boolean; // 远程模式下，是否没有更多数据
      noHover?: boolean; // 是否不显示列表项的 hover 效果
      itemBorder?: boolean; // 是否显示列表项的边框
      draggable?: boolean; // 是否允许拖拽
      disabled?: boolean;
      class?: string;
      itemClass?: string;
      activeItemClass?: string;
      virtualScrollHeight?: string;
      loading?: boolean;
      itemResizable?: boolean;
    }>(),
    {
      mode: 'static',
      keyField: 'key',
      itemHeight: 34,
      bordered: false,
      draggable: false,
      maxHeight: '300px',
      activeItemClass: 'crm-list-item--active',
      itemResizable: false,
      virtualScrollHeight: '100%',
    }
  );

  const emit = defineEmits<{
    (e: 'moreActionSelect', event: ActionsItem, item: Record<string, any>): void;
    (e: 'moreActionsClose'): void;
    (e: 'reachBottom'): void;
    (e: 'itemClick', item: Record<string, any>): void;
    (e: 'clickMore', item: Record<string, any>): void;
    (e: 'wheel', event: Event): void;
  }>();

  const listData = defineModel<Record<string, any>[]>('data', {
    default: [],
  });

  const innerFocusItemKey = defineModel<string>('focusItemKey', {
    default: '',
  });

  const innerActiveItemKey = defineModel<string>('activeItemKey', {
    default: '',
  });

  const { isInitListener, containerStatusClass, setContainer, initScrollListener } = useContainerShadow({
    overHeight: props.itemHeight,
    containerClassName: 'crm-list',
  });

  function handleMoreActionSelect(event: ActionsItem, item: Record<string, any>) {
    innerFocusItemKey.value = item[props.keyField];
    emit('moreActionSelect', event, item);
  }

  function handleMoreActionClose() {
    innerFocusItemKey.value = '';
    emit('moreActionsClose');
  }

  function handleClickMore(item: Record<string, any>) {
    innerFocusItemKey.value = item[props.keyField];
    emit('clickMore', item);
  }

  const listRef: Ref = ref(null);
  /** 提前约 2 条的高度触发加载，减少滚到底部才请求的迟钝感 */
  const REACH_PRELOAD_ITEMS = 2;
  let reachBottomLocked = false;
  let scrollRafId = 0;

  function getScrollContainer() {
    return (listRef.value?.$el?.querySelector('.v-vl') as HTMLElement | null) ?? null;
  }

  function getVirtualContentHeight() {
    return listData.value.length * props.itemHeight;
  }

  function handleReachBottom() {
    if (
      reachBottomLocked ||
      props.mode !== 'remote' ||
      props.noMoreData ||
      props.loading ||
      listData.value.length === 0
    ) {
      return;
    }
    reachBottomLocked = true;
    emit('reachBottom');
  }

  function isContentUnderfill() {
    const scrollContainer = getScrollContainer();
    if (!scrollContainer) {
      return false;
    }
    return getVirtualContentHeight() <= scrollContainer.clientHeight + props.itemHeight;
  }

  function isReachedBottom(target: HTMLElement) {
    const { scrollTop, clientHeight } = target;
    const virtualTotalHeight = getVirtualContentHeight();
    if (virtualTotalHeight <= clientHeight) {
      return true;
    }
    const preloadPx = props.itemHeight * REACH_PRELOAD_ITEMS;
    return scrollTop + clientHeight >= virtualTotalHeight - preloadPx;
  }

  function handleReachBottomScroll(event: Event) {
    const target = (event.currentTarget || event.target) as HTMLElement;
    if (!target) {
      return;
    }
    cancelAnimationFrame(scrollRafId);
    scrollRafId = requestAnimationFrame(() => {
      if (!isReachedBottom(target)) {
        return;
      }
      handleReachBottom();
    });
  }

  function bindScrollContainer() {
    const listContent = getScrollContainer();
    if (!listContent) {
      return;
    }
    setContainer(listContent);
    if (!isInitListener.value) {
      initScrollListener();
    }
  }

  function tryLoadWhenContentUnderfill() {
    if (!isContentUnderfill()) {
      return;
    }
    handleReachBottom();
  }

  function scheduleUnderfillCheck() {
    nextTick(() => {
      requestAnimationFrame(() => {
        tryLoadWhenContentUnderfill();
      });
    });
  }

  // TODO 暂时还未做拖拽
  watch(
    () => [listData.value.length, props.virtualScrollHeight],
    () => {
      if (listData.value.length > 0) {
        if (props.draggable) {
          if (props.virtualScrollHeight) {
            useDraggable('.crm-list .v-vl', listData, {
              ghostClass: 'crm-list-ghost',
            });
          } else {
            useDraggable('.v-vl', listData, {
              ghostClass: 'crm-list-ghost',
            });
          }
        }
        nextTick(() => {
          bindScrollContainer();
          scheduleUnderfillCheck();
        });
      }
    },
    {
      immediate: true,
    }
  );

  watch(
    () => [listData.value.length, props.loading, props.noMoreData],
    ([, loading]) => {
      if (!loading) {
        reachBottomLocked = false;
        scheduleUnderfillCheck();
      }
    }
  );

  function handleWheel(event: Event) {
    emit('wheel', event);
  }
</script>

<style lang="less">
  .crm-list {
    width: calc(100% + 5px);
    .crm-container--shadow-y();
    .v-vl-visible-items {
      padding-right: 5px;
    }
    .crm-list-item {
      border-radius: var(--border-radius-small);
      @apply flex w-full cursor-pointer items-center justify-between;
      .crm-list-item-actions {
        @apply invisible flex items-center justify-end;
      }
      &:hover {
        background: var(--primary-7);
        // TODO 暂时没有拖拽
        .crm-list-drag-icon {
          @apply visible;
        }
        .crm-list-item-actions {
          @apply visible;
        }
      }
    }
    .crm-list-item--no-hover {
      @apply cursor-auto;
      &:hover {
        background-color: transparent;
      }
    }
    .crm-list-item--bordered {
      border: 1px solid var(--text-n8);
    }
    .crm-list-item--focus {
      background-color: var(--primary-7);
      .crm-list-item-actions {
        @apply visible;
      }
    }
    .crm-list-item--active {
      color: var(--primary-8);
    }
  }
  .crm-list-ghost {
    opacity: 0.5;
  }
</style>
