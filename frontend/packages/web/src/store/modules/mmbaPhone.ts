import { defineStore } from 'pinia';

import type { MmbaPhonePreference, MmbaPhonePreferenceUpdateParams } from '@lib/shared/models/mmba/phone';

import { getMmbaPhonePreference, updateMmbaPhonePreference } from '@/api/modules';
import useUserStore from '@/store/modules/user';

interface MmbaPhoneState {
  preference: MmbaPhonePreference;
  loadedContextKey: string;
  loading: boolean;
  saving: boolean;
}

const emptyPreference = (): MmbaPhonePreference => ({
  defaultCardSlotNum: null,
  umConfigured: false,
  deviceConfigured: false,
  cardSlots: [],
});

const useMmbaPhoneStore = defineStore('mmbaPhone', {
  state: (): MmbaPhoneState => ({
    preference: emptyPreference(),
    loadedContextKey: '',
    loading: false,
    saving: false,
  }),
  getters: {
    currentContextKey() {
      const { userInfo } = useUserStore();
      return `${userInfo.tenantId || ''}:${userInfo.id || ''}`;
    },
    loaded(state): boolean {
      return Boolean(this.currentContextKey) && state.loadedContextKey === this.currentContextKey;
    },
  },
  actions: {
    async loadPreference(force = false) {
      if (!force && this.loaded) {
        return this.preference;
      }
      this.loading = true;
      try {
        const response = await getMmbaPhonePreference();
        this.preference = response;
        this.loadedContextKey = this.currentContextKey;
        return response;
      } finally {
        this.loading = false;
      }
    },
    async savePreference(data: MmbaPhonePreferenceUpdateParams) {
      this.saving = true;
      try {
        const response = await updateMmbaPhonePreference(data);
        this.preference = response;
        this.loadedContextKey = this.currentContextKey;
        return response;
      } finally {
        this.saving = false;
      }
    },
    resetPreference() {
      this.preference = emptyPreference();
      this.loadedContextKey = '';
      this.loading = false;
      this.saving = false;
    },
  },
});

export default useMmbaPhoneStore;
