import { FormDesignKeyEnum } from '@lib/shared/enums/formDesignEnum';
import { useI18n } from '@lib/shared/hooks/useI18n';

const { t } = useI18n();
export interface QuickAccessItem {
  label: string;
  key: FormDesignKeyEnum;
  icon: string;
  permission: string[];
  enable: boolean;
}

export const quickAccessList: QuickAccessItem[] = [
  {
    key: FormDesignKeyEnum.CUSTOMER,
    icon: 'newCustomer',
    label: t('customer.new'),
    permission: ['CUSTOMER_MANAGEMENT:ADD'],
    enable: true,
  },
  {
    key: FormDesignKeyEnum.CONTACT,
    icon: 'newContact',
    label: t('customManagement.newContact'),
    permission: ['CUSTOMER_MANAGEMENT_CONTACT:ADD'],
    enable: false,
  },
  {
    key: FormDesignKeyEnum.CLUE,
    icon: 'newClue',
    label: t('clueManagement.newClue'),
    permission: ['CLUE_MANAGEMENT:ADD'],
    enable: false,
  },
  {
    key: FormDesignKeyEnum.BUSINESS,
    icon: 'newOpportunity',
    label: t('opportunity.new'),
    permission: ['OPPORTUNITY_MANAGEMENT:ADD'],
    enable: false,
  },
  {
    key: FormDesignKeyEnum.CONTRACT,
    icon: 'newContract',
    label: t('contract.new'),
    permission: ['CONTRACT:ADD'],
    enable: true,
  },
  {
    key: FormDesignKeyEnum.INVOICE,
    icon: 'newInvoice',
    label: t('invoice.new'),
    permission: ['CONTRACT_INVOICE:ADD'],
    enable: false,
  },
  {
    key: FormDesignKeyEnum.FOLLOW_RECORD,
    icon: 'newRecord',
    label: t('workbench.createFollowUpRecord'),
    permission: ['CUSTOMER_MANAGEMENT:UPDATE', 'CLUE_MANAGEMENT:UPDATE'],
    enable: true,
  },
  {
    key: FormDesignKeyEnum.FOLLOW_PLAN,
    icon: 'newPlan',
    label: t('workbench.createFollowUpPlan'),
    permission: ['CUSTOMER_MANAGEMENT:UPDATE', 'CLUE_MANAGEMENT:UPDATE'],
    enable: true,
  },
  {
    key: FormDesignKeyEnum.ORDER,
    icon: 'newOrder',
    label: t('order.new'),
    permission: ['ORDER:ADD'],
    enable: false,
  },
];

export default {};
