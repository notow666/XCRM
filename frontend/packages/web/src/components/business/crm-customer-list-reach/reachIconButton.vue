<template>
  <span
    class="reach-icon"
    :class="{ 'reach-icon--disabled': disabled }"
    :title="title"
    :role="disabled ? undefined : 'button'"
    :tabindex="disabled ? undefined : 0"
    @click.stop="handleClick"
    @keydown.enter.prevent="handleClick"
  >
    <span class="iconfont-reach reach-icon__glyph" :class="iconClass"
          :style="{ color, width: props.iconSize, height: props.iconSize, fontSize: props.iconSize, lineHeight: props.iconSize }" />
  </span>
</template>

<script setup lang="ts">
  import '@/assets/icon-font-reach/iconfont.css';

  const props = defineProps<{
    title: string;
    iconClass: string;
    color: string;
    disabled?: boolean;
    iconSize: string;
  }>();

  const emit = defineEmits<{
    (e: 'click'): void;
  }>();

  function handleClick() {
    if (props.disabled) return;
    emit('click');
  }
</script>

<style lang="less" scoped>
  .reach-icon {
    position: relative;
    display: inline-flex;
    flex-shrink: 0;
    cursor: pointer;
    align-items: center;
    justify-content: center;
  }
  .reach-icon--disabled {
    cursor: default;
  }
  .reach-icon__glyph {
    display: inline-block;
    width: 22px;
    height: 22px;
    font-size: 22px;
    line-height: 22px;
    text-align: center;

    &::before {
      display: inline-block;
      line-height: 1;
    }
  }
</style>
