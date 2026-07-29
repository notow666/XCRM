export type MmbaPhoneCardSlotNum = 1 | 2;

export interface MmbaPhoneCardSlot {
  cardSlotNum: MmbaPhoneCardSlotNum;
  available: boolean;
  phone?: string;
}

export interface MmbaPhonePreference {
  defaultCardSlotNum?: MmbaPhoneCardSlotNum | null;
  umConfigured: boolean;
  deviceConfigured: boolean;
  cardSlots: MmbaPhoneCardSlot[];
}

export interface MmbaPhonePreferenceUpdateParams {
  defaultCardSlotNum: MmbaPhoneCardSlotNum | null;
}
