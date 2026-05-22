<template>
  <div class="crm-customer-list-reach flex shrink-0 flex-col gap-[6px]">
    <ReachIconButton
      icon-class="icon-a-dianhuadianhua-icon"
      :color="callIconColor"
      :title="t('customer.reach.call')"
      :disabled="!canOperate"
      @click="handleCallClick"
    />
    <ReachIconButton
      icon-class="icon-weixin"
      :color="wechatIconColor"
      :title="wechatIconTitle"
      :disabled="!canOperate"
      @click="handleWechatClick"
    />
    <ReachIconButton
      v-if="canOperate"
      icon-class="icon-duanxin"
      color="#F7B52C"
      :title="t('customer.reach.sms')"
      @click="handleSmsClick"
    />
  </div>
</template>

<script setup lang="ts">
  import { useMessage } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';

  import ReachIconButton from './reachIconButton.vue';

  const CALL_COLOR_ACTIVE = '#1296db';
  const CALL_COLOR_INACTIVE = '#8a8a8a';
  const WECHAT_COLOR_ACTIVE = '#28C445';
  const WECHAT_COLOR_INACTIVE = '#8a8a8a';
  const DEFAULT_CARD_SLOT = 1;

  const props = withDefaults(
    defineProps<{
      callStatus: number;
      wechatFriendStatus: number;
      /** 负责人为当前登录用户时可操作（拨打/短信/微信）；否则仅展示电话、微信状态色且不可点击 */
      canOperate?: boolean;
    }>(),
    {
      canOperate: true,
    }
  );

  const emit = defineEmits<{
    (e: 'dial', cardSlotNum: number): void;
    (e: 'sms', cardSlotNum: number): void;
    (e: 'wechat'): void;
    (e: 'addWechat'): void;
  }>();

  const { t } = useI18n();
  const message = useMessage();

  const callIconColor = computed(() =>
    props.callStatus === 2 ? CALL_COLOR_ACTIVE : CALL_COLOR_INACTIVE
  );

  const wechatIconColor = computed(() =>
    props.wechatFriendStatus === 2 ? WECHAT_COLOR_ACTIVE : WECHAT_COLOR_INACTIVE
  );

  const wechatIconTitle = computed(() => {
    if (props.wechatFriendStatus === 2) {
      return t('customer.reach.wechat');
    }
    return t('customer.reach.addWechat');
  });

  function handleCallClick() {
    if (!props.canOperate) return;
    emit('dial', DEFAULT_CARD_SLOT);
  }

  function handleSmsClick() {
    if (!props.canOperate) return;
    emit('sms', DEFAULT_CARD_SLOT);
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
