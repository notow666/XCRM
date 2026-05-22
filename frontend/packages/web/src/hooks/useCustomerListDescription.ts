import { useI18n } from '@lib/shared/hooks/useI18n';

export interface CustomerListDescriptionItem {
  label: string;
  value: string;
  fullLine?: boolean;
}

export interface CustomerStageTagStyle {
  bgColor: string;
  color: string;
}

const DEFAULT_STAGE_TAG_STYLE: CustomerStageTagStyle = {
  bgColor: 'var(--info-5)',
  color: '#00a6ab',
};

function stageTagStyleByType(type: 'success' | 'error' | 'info'): CustomerStageTagStyle {
  const map: Record<'success' | 'error' | 'info', CustomerStageTagStyle> = {
    success: { bgColor: 'var(--success-5)', color: 'var(--success-green)' },
    error: { bgColor: 'var(--error-5)', color: 'var(--error-red)' },
    info: DEFAULT_STAGE_TAG_STYLE,
  };
  return map[type];
}

export function getCustomerStageTagStyle(
  stageId: string | undefined,
  stageConfigList: Array<{ id: string; type?: string; rate?: string | number }> = []
): CustomerStageTagStyle {
  if (!stageId || !stageConfigList.length) {
    return DEFAULT_STAGE_TAG_STYLE;
  }
  const stage = stageConfigList.find((s) => s.id === stageId);
  if (!stage) {
    return DEFAULT_STAGE_TAG_STYLE;
  }
  const { type, rate } = stage;
  if (type === 'END') {
    if (String(rate) === '100') return stageTagStyleByType('success');
    if (String(rate) === '0') return stageTagStyleByType('error');
  }
  return DEFAULT_STAGE_TAG_STYLE;
}

export const CUSTOMER_LEVEL_STAR_MAX = 5;

const CUSTOMER_LEVEL_CN_MAP: Record<string, number> = {
  一: 1,
  二: 2,
  三: 3,
  四: 4,
  五: 5,
};

/** 与表单配置一致，用于本地更新后动态列展示 */
const CUSTOMER_LEVEL_LABEL_MAP: Record<number, string> = {
  1: '一星',
  2: '二星',
  3: '三星',
  4: '四星',
  5: '五星',
};

function getCustomerLevelDisplayLabel(level: number, customerLevelFieldId: string, row?: Record<string, any>): string {
  const fieldValue = String(level);
  const options = row?.optionMap?.[customerLevelFieldId] as Array<{ id?: string; name?: string }> | undefined;
  const fromOptionMap = options?.find((e) => e.id === fieldValue)?.name;
  if (fromOptionMap) {
    return fromOptionMap;
  }
  return CUSTOMER_LEVEL_LABEL_MAP[level] ?? fieldValue;
}

function parseCustomerLevelStarCount(value: unknown): number {
  if (value === undefined || value === null || value === '') {
    return 0;
  }
  const num = Number(value);
  if (Number.isFinite(num) && num >= 1 && num <= 5) {
    return Math.floor(num);
  }
  const text = String(value).trim();
  const digitMatch = text.match(/^(\d)/);
  if (digitMatch) {
    const digit = Number(digitMatch[1]);
    return digit >= 1 && digit <= 5 ? digit : 0;
  }
  const cnMatch = text.match(/^([一二三四五])/);
  if (cnMatch) {
    return CUSTOMER_LEVEL_CN_MAP[cnMatch[1]] ?? 0;
  }
  return 0;
}

/** 根据客户等级字段解析应展示的星星数量（1–5） */
export function getCustomerLevelStarCount(row: Record<string, any>, customerLevelFieldId?: string) {
  if (!customerLevelFieldId) {
    return 0;
  }
  const moduleField = row.moduleFields?.find(
    (field: { fieldId?: string; fieldValue?: unknown }) => field.fieldId === customerLevelFieldId
  );
  const raw = moduleField?.fieldValue ?? row[customerLevelFieldId];
  return parseCustomerLevelStarCount(raw);
}

/** 列表行内更新客户等级（与 moduleFields 保持一致） */
export function setCustomerLevelOnRow(row: Record<string, any>, customerLevelFieldId: string, level: number) {
  const fieldValue = String(level);
  if (!row.moduleFields) {
    row.moduleFields = [];
  }
  const moduleField = row.moduleFields.find((field: { fieldId?: string }) => field.fieldId === customerLevelFieldId);
  if (moduleField) {
    moduleField.fieldValue = fieldValue;
  } else {
    row.moduleFields.push({ fieldId: customerLevelFieldId, fieldValue });
  }
  // 动态列展示的是选项文案（与 parseFormDetail 列表转换一致），非选项 value
  row[customerLevelFieldId] = getCustomerLevelDisplayLabel(level, customerLevelFieldId, row);
}

/** 列表行内更新 INPUT_MULTIPLE 标签（与 moduleFields 保持一致） */
export function setInputMultipleTagsOnRow(row: Record<string, any>, fieldId: string, tags: string[]) {
  const fieldValue = [...tags];
  if (!row.moduleFields) {
    row.moduleFields = [];
  }
  const moduleField = row.moduleFields.find((field: { fieldId?: string }) => field.fieldId === fieldId);
  if (moduleField) {
    moduleField.fieldValue = fieldValue;
  } else {
    row.moduleFields.push({ fieldId, fieldValue });
  }
  row[fieldId] = fieldValue;
}

export function formatCustomerStageLabel(row: Record<string, any>) {
  if (!row.stageName && !row.stageStatus) return '';
  const prefixMap: Record<string, string> = {
    NEW: '待',
    IN_PROGRESS: '',
    COMPLETED: '已',
    FAILED: '',
  };
  const suffixMap: Record<string, string> = {
    NEW: '',
    IN_PROGRESS: '中',
    COMPLETED: '',
    FAILED: '',
  };
  const status = row.stageStatus || '';
  const prefix = status ? prefixMap[status] || '' : '';
  const suffix = status ? suffixMap[status] || '' : '';
  const name = row.stageName || '';
  return `${prefix}${name}${suffix}` || '';
}

export function useCustomerListDescription() {
  const { t } = useI18n();

  function buildDescription(row: Record<string, any>): CustomerListDescriptionItem[] {
    const items: CustomerListDescriptionItem[] = [];
    if (row.ownerName) {
      items.push({ label: t('opportunity.owner'), value: row.ownerName });
    }
    if (row.reservedDays !== undefined && row.reservedDays !== null && row.reservedDays !== '') {
      const days = Number(row.reservedDays);
      items.push({
        label: t('customer.remainingVesting'),
        value: Number.isNaN(days) ? '-' : `${days}${t('common.dayUnit')}`,
      });
    }
    return items;
  }

  function buildStatusDescription(
    row: Record<string, any>,
    getCallStatusText: (row: Record<string, any>) => string,
    getWxFriendStatusText: (row: Record<string, any>) => string
  ): CustomerListDescriptionItem[] {
    return [
      { label: t('customer.callStatus'), value: getCallStatusText(row) },
      { label: t('customer.wechatFriendStatus'), value: getWxFriendStatusText(row) },
    ];
  }

  /** 列表紧凑模式：单行摘要，减少卡片高度 */
  function buildListSummaryLine(
    row: Record<string, any>,
    getCallStatusText: (row: Record<string, any>) => string,
    getWxFriendStatusText: (row: Record<string, any>) => string
  ) {
    const parts: string[] = [];
    if (row.ownerName) {
      parts.push(`${t('opportunity.owner')} ${row.ownerName}`);
    }
    if (row.reservedDays !== undefined && row.reservedDays !== null && row.reservedDays !== '') {
      const days = Number(row.reservedDays);
      parts.push(`${t('customer.remainingVesting')} ${Number.isNaN(days) ? '-' : `${days}${t('common.dayUnit')}`}`);
    }
    parts.push(`${t('customer.callStatus')} ${getCallStatusText(row)}`);
    parts.push(`${t('customer.wechatFriendStatus')} ${getWxFriendStatusText(row)}`);
    return parts.join(' · ');
  }

  return {
    buildDescription,
    buildStatusDescription,
    buildListSummaryLine,
    formatCustomerStageLabel,
  };
}
