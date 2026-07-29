<template>
  <div
    class="crm-customer-list-reach flex shrink-0 gap-[6px]"
    :class="props.direction === 'row' ? 'flex-row items-center' : 'flex-col'"
  >
    <n-dropdown
      v-if="!props.defaultCardSlotNum"
      trigger="click"
      :options="dialCardSlotOptions"
      placement="bottom-start"
      :disabled="!canDial"
      @select="handleDialSelect"
    >
      <ReachIconButton
        icon-class="icon-a-dianhuadianhua-icon"
        :color="callIconColor"
        :icon-size="iconSize"
        :title="t('customer.reach.call')"
        :disabled="!canDial"
      />
    </n-dropdown>
    <ReachIconButton
      v-else
      icon-class="icon-a-dianhuadianhua-icon"
      :color="callIconColor"
      :icon-size="iconSize"
      :title="t('customer.reach.call')"
      :disabled="!canDial"
      @click="handleDialClick"
    />
    <ReachIconButton
      icon-class="icon-weixin"
      :color="wechatIconColor"
      :icon-size="iconSize"
      :title="wechatIconTitle"
      :disabled="!canOperate"
      @click="handleWechatClick"
    />
    <n-dropdown
      v-if="canOperate"
      trigger="click"
      :options="dialCardSlotOptions"
      placement="bottom-start"
      @select="handleSmsSelect"
    >
      <ReachIconButton
        icon-class="icon-duanxin"
        color="#F7B52C"
        :icon-size="iconSize"
        :title="t('customer.reach.sms')"
      />
    </n-dropdown>
  </div>
</template>

<script setup lang="ts">
  import { NDropdown, useMessage } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';

  import ReachIconButton from './reachIconButton.vue';

  const CALL_COLOR_ACTIVE = '#1296db';
  const CALL_COLOR_INACTIVE = '#8a8a8a';
  const WECHAT_COLOR_ACTIVE = '#28C445';
  const WECHAT_COLOR_INACTIVE = '#8a8a8a';
  const dialCardSlotOptions = [
    { label: '卡槽1', key: 1 },
    { label: '卡槽2', key: 2 },
  ];

  const props = withDefaults(
    defineProps<{
      callStatus: number;
      wechatFriendStatus: number;
      /** 负责人为当前登录用户时可操作（拨打/短信/微信）；否则仅展示电话、微信状态色且不可点击 */
      canOperate?: boolean;
      /** 当前用户的默认拨号卡；有值时点击电话图标直接拨号 */
      defaultCardSlotNum?: 1 | 2 | null;
      /** 默认拨号卡配置是否已加载 */
      preferenceLoaded?: boolean;
      /** 详情页标题栏横向排列；列表侧栏默认纵向 */
      direction?: 'row' | 'column';
    }>(),
    {
      canOperate: true,
      defaultCardSlotNum: null,
      preferenceLoaded: false,
      direction: 'column',
    }
  );

  const emit = defineEmits<{
    (e: 'dial', cardSlotNum?: number): void;
    (e: 'sms', cardSlotNum: number): void;
    (e: 'wechat'): void;
    (e: 'addWechat'): void;
  }>();

  const { t } = useI18n();
  const message = useMessage();

  const iconSize = computed(() => (props.direction === 'row' ? '30px' : '22px'));
  const canDial = computed(() => props.canOperate && props.preferenceLoaded);

  const callIconColor = computed(() => (props.callStatus === 2 ? CALL_COLOR_ACTIVE : CALL_COLOR_INACTIVE));

  const wechatIconColor = computed(() =>
    props.wechatFriendStatus === 2 ? WECHAT_COLOR_ACTIVE : WECHAT_COLOR_INACTIVE
  );

  const wechatIconTitle = computed(() => {
    if (props.wechatFriendStatus === 2) {
      return t('customer.reach.wechat');
    }
    return t('customer.reach.addWechat');
  });

  function handleDialSelect(cardSlotNum: string | number) {
    if (!canDial.value) return;
    emit('dial', Number(cardSlotNum));
  }

  function handleDialClick() {
    if (!canDial.value) return;
    emit('dial');
  }

  function handleSmsSelect(cardSlotNum: string | number) {
    if (!props.canOperate) return;
    emit('sms', Number(cardSlotNum));
  }

  function handleWechatClick() {
    if (!props.canOperate) return;
    if (props.wechatFriendStatus === -1) {
      message.warning(t('customer.reach.wechatFriendAwaitingApproval'));
      return;
    }
    if (props.wechatFriendStatus === 2) {
      emit('wechat');
      return;
    }
    if (props.wechatFriendStatus === 0 || props.wechatFriendStatus === 1) {
      emit('addWechat');
    }
  }
</script>
