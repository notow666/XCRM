<template>
  <span class="customer-list-level-stars" role="group" aria-label="customer-level" @click.stop>
    <span
      v-for="star in CUSTOMER_LEVEL_STAR_MAX"
      :key="`level-star-${star}`"
      class="iconfont-reach icon-xingxing customer-list-level-stars__star"
      :class="{
        'customer-list-level-stars__star--filled': star <= level,
        'customer-list-level-stars__star--empty': star > level,
        'customer-list-level-stars__star--clickable': editable && !loading,
        'customer-list-level-stars__star--loading': loading,
      }"
      @click="handleClick(star)"
    />
  </span>
</template>

<script setup lang="ts">
  import '@/assets/icon-font-reach/iconfont.css';

  import { CUSTOMER_LEVEL_STAR_MAX } from '@/hooks/useCustomerListDescription';

  const props = withDefaults(
    defineProps<{
      level: number;
      editable?: boolean;
      loading?: boolean;
    }>(),
    {
      level: 0,
      editable: false,
      loading: false,
    }
  );

  const emit = defineEmits<{
    (e: 'select', level: number): void;
  }>();

  function handleClick(star: number) {
    if (!props.editable || props.loading) return;
    emit('select', star);
  }
</script>

<style lang="less" scoped>
  .customer-list-level-stars {
    display: inline-flex;
    align-items: center;
    gap: 2px;
    flex-shrink: 0;
  }
  .customer-list-level-stars__star {
    display: inline-block;
    width: 14px;
    height: 14px;
    font-size: 14px;
    line-height: 14px;
    text-align: center;

    &::before {
      display: inline-block;
      line-height: 1;
    }
  }
  .customer-list-level-stars__star--empty {
    color: transparent;
    -webkit-text-stroke: 1px #c9cdd4;
  }
  .customer-list-level-stars__star--filled {
    color: #f7b52c;
    -webkit-text-stroke: 0;
  }
  .customer-list-level-stars__star--clickable {
    cursor: pointer;
  }
  .customer-list-level-stars__star--loading {
    cursor: wait;
    opacity: 0.6;
  }
</style>
