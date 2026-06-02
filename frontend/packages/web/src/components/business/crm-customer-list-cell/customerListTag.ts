import type { CrmTagGroupProps } from '@/components/pure/crm-tag-group/index.vue';

export const CUSTOMER_LIST_TAG_MAX_ROWS = 3;
export const CUSTOMER_LIST_TAG_MAX_LABEL_LENGTH = 5;

export type CustomerListTagItem = {
  fullLabel: string;
  displayLabel: string;
};

export function getCustomerListTagLabel(item: string | Record<string, any>, labelKey = 'label'): string {
  if (typeof item === 'string') return item || '-';
  const label = item?.[labelKey];
  if (label === undefined || label === null || label === '') return '-';
  return String(label);
}

export function formatCustomerListTagLabel(label: string) {
  if (label === '-') return label;
  if (label.length <= CUSTOMER_LIST_TAG_MAX_LABEL_LENGTH) return label;
  return label.slice(0, CUSTOMER_LIST_TAG_MAX_LABEL_LENGTH);
}

export function normalizeCustomerListTags(
  tags: string[] | Record<string, any>[] | null | undefined,
  labelKey = 'label'
): CustomerListTagItem[] {
  if (!tags?.length) return [];
  return tags
    .filter((item) => item !== undefined && item !== null && item !== '')
    .map((item) => {
      const fullLabel = getCustomerListTagLabel(item, labelKey);
      return {
        fullLabel,
        displayLabel: formatCustomerListTagLabel(fullLabel),
      };
    });
}

/** 轻量列表行：标签列纯文本展示 */
export function formatCustomerListTagsPlain(
  tags: string[] | Record<string, any>[] | null | undefined,
  labelKey = 'label'
): string {
  const items = normalizeCustomerListTags(tags, labelKey);
  if (!items.length) return '-';
  return items.map((item) => item.fullLabel).join('、');
}

export function getCustomerListTagBindProps(tagGroupProps?: Omit<CrmTagGroupProps, 'tags'>) {
  return {
    size: tagGroupProps?.size ?? 'medium',
    type: tagGroupProps?.type ?? 'default',
    theme: tagGroupProps?.theme ?? 'dark',
  };
}

export function splitCustomerListVisibleTags(items: CustomerListTagItem[]) {
  if (items.length <= CUSTOMER_LIST_TAG_MAX_ROWS) {
    return { visible: items, hidden: [] as CustomerListTagItem[] };
  }
  return {
    visible: items.slice(0, CUSTOMER_LIST_TAG_MAX_ROWS - 1),
    hidden: items.slice(CUSTOMER_LIST_TAG_MAX_ROWS - 1),
  };
}
