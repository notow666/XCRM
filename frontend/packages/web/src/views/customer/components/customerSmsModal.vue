<template>
  <CrmModal
    v-model:show="visible"
    size="small"
    :ok-loading="loading"
    :positive-text="positiveText"
    @confirm="handleConfirm"
  >
    <template #title>
      <div class="flex gap-[4px] overflow-hidden">
        <div class="text-[var(--text-n1)]">{{ modalTitle }}</div>
        <div class="flex text-[var(--text-n4)]">
          (
          <div class="one-line-text max-w-[300px]">{{ props.name || props.mobile }}</div>
          )
        </div>
      </div>
    </template>
    <n-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-placement="left"
      label-width="auto"
      require-mark-placement="right"
    >
      <n-form-item v-if="showWechatSelector" path="selectedWechatId" :label="t('customer.reach.selectWechat')">
        <n-select
          v-model:value="form.selectedWechatId"
          :options="props.wechatOptions"
          :placeholder="t('customer.reach.selectWechatPlaceholder')"
          filterable
          clearable
        />
      </n-form-item>
      <n-form-item v-if="showContentInput" path="msg" :label="contentLabel">
        <n-input
          v-model:value="form.msg"
          type="textarea"
          :placeholder="contentPlaceholder"
          allow-clear
          :maxlength="contentMaxLength"
          show-count
        />
      </n-form-item>
      <n-form-item v-if="showFriendNoteInput" path="note" :label="t('common.remark')">
        <n-input
          v-model:value="form.note"
          type="textarea"
          :placeholder="t('customer.reach.friendNotePlaceholder')"
          allow-clear
          :maxlength="32"
          show-count
        />
      </n-form-item>
      <n-form-item v-if="showFriendDescriptionInput" path="description" :label="t('customer.reach.friendDescription')">
        <n-input
          v-model:value="form.description"
          type="textarea"
          :placeholder="t('customer.reach.friendDescriptionPlaceholder')"
          allow-clear
          :maxlength="400"
          show-count
        />
      </n-form-item>
    </n-form>
  </CrmModal>
</template>

<script setup lang="ts">
  import { nextTick } from 'vue';
  import {
    FormInst,
    FormItemRule,
    FormRules,
    NForm,
    NFormItem,
    NInput,
    NSelect,
    SelectOption,
  } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';

  import CrmModal from '@/components/pure/crm-modal/index.vue';

  interface WechatOption extends SelectOption {
    label: string;
    value: string;
    wxId: string;
    wxPhone: string;
  }

  const props = defineProps<{
    mode: 'sms' | 'wx' | 'wxFriend';
    sourceId: string;
    name: string;
    mobile: string;
    wechatOptions?: WechatOption[];
  }>();

  const emit = defineEmits<{
    (e: 'submit', payload: { msg: string; note: string; description: string; selectedWechat?: WechatOption }): void;
  }>();

  const visible = defineModel<boolean>('show', { required: true });

  const { t } = useI18n();

  const loading = ref(false);
  const formRef = ref<FormInst | null>(null);
  const wxFriendStep = ref<'select' | 'content'>('select');
  const form = ref<{ msg: string; note: string; description: string; selectedWechatId: string | null }>({
    msg: '',
    note: '',
    description: '',
    selectedWechatId: null,
  });

  const showWechatSelector = computed(() => props.mode === 'wxFriend' && wxFriendStep.value === 'select');
  const showContentInput = computed(
    () => props.mode === 'sms' || props.mode === 'wx' || wxFriendStep.value === 'content'
  );
  const showFriendNoteInput = computed(() => props.mode === 'wxFriend' && wxFriendStep.value === 'content');
  const showFriendDescriptionInput = computed(() => props.mode === 'wxFriend' && wxFriendStep.value === 'content');
  const modalTitle = computed(() => {
    if (props.mode === 'sms') {
      return t('customer.reach.sms');
    }
    if (props.mode === 'wx') {
      return t('customer.reach.wechat');
    }
    return t('customer.reach.addWechat');
  });
  const positiveText = computed(() => {
    if (props.mode === 'sms') {
      return t('customer.reach.sendSms');
    }
    if (props.mode === 'wx') {
      return t('customer.reach.sendWechat');
    }
    return wxFriendStep.value === 'select' ? t('customer.reach.nextStep') : t('customer.reach.confirmAddWechat');
  });
  const contentLabel = computed(() => {
    if (props.mode === 'sms') {
      return t('customer.reach.smsContent');
    }
    if (props.mode === 'wx') {
      return t('customer.reach.wechatContent');
    }
    return t('customer.reach.verifyInfo');
  });
  const contentPlaceholder = computed(() => {
    if (props.mode === 'sms') {
      return t('customer.reach.smsPlaceholder');
    }
    if (props.mode === 'wx') {
      return t('customer.reach.wechatPlaceholder');
    }
    return t('customer.reach.verifyPlaceholder');
  });
  const contentMaxLength = computed(() => {
    if (props.mode === 'wx') {
      return 2048;
    }
    if (props.mode === 'wxFriend') {
      return 100;
    }
    return 500;
  });
  const selectedWechat = computed(() =>
    (props.wechatOptions || []).find((item) => item.value === form.value.selectedWechatId)
  );

  function getBusinessTextLength(value: string): number {
    return Array.from(value || '').reduce((length, char) => {
      return length + (char.charCodeAt(0) > 255 ? 2 : 1);
    }, 0);
  }

  function validateBusinessTextLength(limit: number, message: string) {
    return (_rule: FormItemRule, value: string) => {
      if (!value) {
        return true;
      }
      if (getBusinessTextLength(value.trim()) > limit) {
        return new Error(message);
      }
      return true;
    };
  }

  const rules = computed<FormRules>(() => {
    return {
      selectedWechatId: showWechatSelector.value
        ? [
            {
              trigger: ['change', 'blur'],
              required: true,
              message: t('common.notNull', { value: t('customer.reach.selectWechat') }),
            },
          ]
        : [],
      msg: showContentInput.value
        ? [
            {
              trigger: ['input', 'blur'],
              required: true,
              message: t('common.notNull', { value: contentLabel.value }),
            },
            ...(props.mode === 'wxFriend'
              ? [
                  {
                    trigger: ['input', 'blur'],
                    validator: validateBusinessTextLength(100, t('customer.reach.verifyInfoLengthTip')),
                  },
                ]
              : []),
          ]
        : [],
      note: showFriendNoteInput.value
        ? [
            {
              trigger: ['input', 'blur'],
              validator: validateBusinessTextLength(32, t('customer.reach.friendNoteLengthTip')),
            },
          ]
        : [],
    };
  });

  async function handleConfirm() {
    try {
      loading.value = true;
      await formRef.value?.validate();
      if (showWechatSelector.value) {
        wxFriendStep.value = 'content';
        await nextTick();
        formRef.value?.restoreValidation();
        return;
      }
      emit('submit', {
        msg: form.value.msg.trim(),
        note: form.value.note.trim(),
        description: form.value.description.trim(),
        selectedWechat: selectedWechat.value,
      });
      visible.value = false;
    } catch (e) {
      return;
    } finally {
      loading.value = false;
    }
  }

  function resetFormState() {
    wxFriendStep.value = 'select';
    form.value = {
      msg: '',
      note: '',
      description: '',
      selectedWechatId: null,
    };
    nextTick(() => {
      formRef.value?.restoreValidation();
    });
  }

  watch(
    () => visible.value,
    (val) => {
      if (val) {
        resetFormState();
      }
    }
  );

  watch(
    () => props.mode,
    () => {
      if (visible.value) {
        resetFormState();
      }
    }
  );
</script>
