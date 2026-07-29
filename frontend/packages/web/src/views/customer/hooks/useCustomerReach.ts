import { ref } from 'vue';
import { storeToRefs } from 'pinia';
import { useMessage } from 'naive-ui';

import { useI18n } from '@lib/shared/hooks/useI18n';
import { assertMmbaResponseSuccess } from '@lib/shared/method/mmbaResponse';

import {
  addCustomerWxFriend,
  dialCustomerPhone,
  getPersonalWechat,
  sendCustomerSms,
  sendCustomerWechat,
} from '@/api/modules';
import useMmbaPhoneStore from '@/store/modules/mmbaPhone';
import useUserStore from '@/store/modules/user';

export interface CustomerReachRow {
  id: string;
  name?: string;
  owner?: string;
  callStatus?: number;
  wechatFriendStatus?: number;
  mobile?: string;
  inSharedPool?: boolean;
  [key: string]: unknown;
}

type ReachModalMode = 'sms' | 'wx' | 'wxFriend';

interface ActiveWechatOption {
  label: string;
  value: string;
  wxId: string;
  wxPhone: string;
}

export function useCustomerReach(options?: { onStatusUpdated?: () => void }) {
  const { t } = useI18n();
  const Message = useMessage();
  const userStore = useUserStore();
  const mmbaPhoneStore = useMmbaPhoneStore();
  const { preference: dialPreference, loaded: dialPreferenceLoaded } = storeToRefs(mmbaPhoneStore);
  const defaultCallCardSlotNum = computed(() => dialPreference.value.defaultCardSlotNum || null);
  const dialingCustomerIds = new Set<string>();

  const showReachModal = ref(false);
  const reachModal = ref<{
    mode: ReachModalMode;
    sourceId: string;
    name: string;
    mobile: string;
    cardSlotNum: number;
  }>({
    mode: 'sms',
    sourceId: '',
    name: '',
    mobile: '',
    cardSlotNum: 1,
  });
  const activeReachRow = ref<CustomerReachRow>();
  const activeWechatOptions = ref<ActiveWechatOption[]>([]);

  function readCallStatus(row: CustomerReachRow) {
    if (Number.isInteger(row.callStatus)) {
      return row.callStatus as number;
    }
    return 0;
  }

  function readWechatFriendStatus(row: CustomerReachRow) {
    if (Number.isInteger(row.wechatFriendStatus)) {
      return row.wechatFriendStatus as number;
    }
    return 0;
  }

  function isCustomerInSharedPool(row: CustomerReachRow) {
    return row.inSharedPool === true;
  }

  function markReachStatusInitiated(row: CustomerReachRow, field: 'callStatus' | 'wechatFriendStatus'): boolean {
    if (isCustomerInSharedPool(row)) {
      return false;
    }
    const currentStatus = field === 'callStatus' ? readCallStatus(row) : readWechatFriendStatus(row);
    if (currentStatus !== 0) {
      return false;
    }
    row[field] = -1;
    return true;
  }

  function isReachOwner(row: CustomerReachRow) {
    return String(row.owner ?? '') === String(userStore.userInfo.id ?? '');
  }

  function applyReachInitiatedOnSuccess(row: CustomerReachRow, field: 'callStatus' | 'wechatFriendStatus') {
    if (!markReachStatusInitiated(row, field)) {
      return;
    }
    options?.onStatusUpdated?.();
  }

  function ensureReachOwner(row: CustomerReachRow) {
    if (isReachOwner(row)) {
      return true;
    }
    Message.warning(t('customer.reach.notOwner'));
    return false;
  }

  function logReachActionError(error: unknown) {
    // eslint-disable-next-line no-console
    console.error(error);
    if (error instanceof Error && error.message && !(error as { isAxiosError?: boolean }).isAxiosError) {
      Message.error(error.message);
    }
  }

  mmbaPhoneStore.loadPreference().catch(logReachActionError);

  async function handleDialCustomer(row: CustomerReachRow, cardSlotNum?: number) {
    if (!ensureReachOwner(row)) {
      return;
    }
    if (dialingCustomerIds.has(row.id)) {
      return;
    }
    dialingCustomerIds.add(row.id);
    try {
      const response = await dialCustomerPhone({
        toPhone: '',
        ...(cardSlotNum ? { cardSlotNum } : {}),
        bizExtInfo: {
          customerId: row.id,
        },
      });
      assertMmbaResponseSuccess(response);
      applyReachInitiatedOnSuccess(row, 'callStatus');
      Message.success(t('customer.reach.dialSending'));
    } catch (error) {
      logReachActionError(error);
    } finally {
      dialingCustomerIds.delete(row.id);
    }
  }

  function openReachModal(row: CustomerReachRow, mode: ReachModalMode, cardSlotNum = 1) {
    activeReachRow.value = row;
    reachModal.value = {
      mode,
      sourceId: row.id,
      name: row.name || '',
      mobile: (row.mobile as string) || '',
      cardSlotNum,
    };
    showReachModal.value = true;
  }

  function handleSmsCustomer(row: CustomerReachRow, cardSlotNum: number) {
    if (!ensureReachOwner(row)) {
      return;
    }
    if (!row.mobile) {
      Message.warning(t('customer.reach.phoneMissing'));
      return;
    }
    openReachModal(row, 'sms', cardSlotNum);
  }

  function handleWechatCustomer(row: CustomerReachRow) {
    if (!ensureReachOwner(row)) {
      return;
    }
    if (!row.mobile) {
      Message.warning(t('customer.reach.wechatPhoneMissing'));
      return;
    }
    openReachModal(row, 'wx');
  }

  async function ensureActiveWechatOptions() {
    const response = await getPersonalWechat();
    activeWechatOptions.value = (response.wechats || [])
      .filter((item) => item.mappingStatus === 'ACTIVE' && item.wxId && item.wxPhone)
      .map((item) => ({
        label: item.wxNickName || item.wxAccount || item.wxId,
        value: item.wxId,
        wxId: item.wxId,
        wxPhone: item.wxPhone,
      }));
  }

  async function handleWechatFriendCustomer(row: CustomerReachRow) {
    if (!ensureReachOwner(row)) {
      return;
    }
    if (!row.mobile) {
      Message.warning(t('customer.reach.addWechatPhoneMissing'));
      return;
    }
    const loadingMessage = Message.loading(t('customer.reach.wechatLoading'), { duration: 0 });
    try {
      await ensureActiveWechatOptions();
      if (!activeWechatOptions.value.length) {
        Message.warning(t('customer.reach.noActiveWechat'));
        return;
      }
      openReachModal(row, 'wxFriend');
    } catch (error) {
      logReachActionError(error);
    } finally {
      loadingMessage.destroy();
    }
  }

  async function handleReachModalSubmit(payload: {
    msg: string;
    note: string;
    description: string;
    selectedWechat?: ActiveWechatOption;
  }) {
    try {
      if (reachModal.value.mode === 'sms') {
        await sendCustomerSms({
          cardSlotNum: reachModal.value.cardSlotNum,
          toPhone: '',
          msg: payload.msg,
          bizExtInfo: {
            customerId: reachModal.value.sourceId,
          },
        });
        Message.success(t('customer.reach.smsSending'));
        return;
      }
      if (reachModal.value.mode === 'wx') {
        await sendCustomerWechat({
          customerId: reachModal.value.sourceId,
          message: payload.msg,
        });
        Message.success(t('customer.reach.wechatSending'));
        return;
      }
      if (!payload.selectedWechat) {
        Message.warning(t('customer.reach.noActiveWechat'));
        return;
      }
      const response = await addCustomerWxFriend({
        vinfo: payload.msg,
        note: payload.note || undefined,
        description: payload.description || undefined,
        friendPhone: '',
        friendSearch: '',
        umPhone: payload.selectedWechat.wxPhone,
        umWxid: payload.selectedWechat.wxId,
        bizExtInfo: {
          customerId: reachModal.value.sourceId,
        },
      });
      assertMmbaResponseSuccess(response);
      if (activeReachRow.value) {
        applyReachInitiatedOnSuccess(activeReachRow.value, 'wechatFriendStatus');
      }
      Message.success(t('customer.reach.addWechatSending'));
      showReachModal.value = false;
    } catch (error) {
      logReachActionError(error);
    }
  }

  return {
    showReachModal,
    reachModal,
    activeWechatOptions,
    defaultCallCardSlotNum,
    dialPreferenceLoaded,
    readCallStatus,
    readWechatFriendStatus,
    isReachOwner,
    handleDialCustomer,
    handleSmsCustomer,
    handleWechatCustomer,
    handleWechatFriendCustomer,
    handleReachModalSubmit,
  };
}
