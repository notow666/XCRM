import { onBeforeUnmount, ref } from 'vue';

/** 滚动停止后恢复完整行渲染的等待时间 */
const DEFAULT_IDLE_MS = 120;

/**
 * 列表纵向滚动时启用轻量行，停止滚动后延迟恢复完整渲染（减少 NEllipsis 等挂载抖动）
 */
export default function useListScrollLightweight(idleMs = DEFAULT_IDLE_MS) {
  const isListScrolling = ref(false);
  let idleTimer: ReturnType<typeof setTimeout> | undefined;

  function markListScrolling() {
    if (!isListScrolling.value) {
      isListScrolling.value = true;
    }
    if (idleTimer !== undefined) {
      clearTimeout(idleTimer);
    }
    idleTimer = setTimeout(() => {
      isListScrolling.value = false;
      idleTimer = undefined;
    }, idleMs);
  }

  onBeforeUnmount(() => {
    if (idleTimer !== undefined) {
      clearTimeout(idleTimer);
    }
  });

  return {
    isListScrolling,
    markListScrolling,
  };
}
