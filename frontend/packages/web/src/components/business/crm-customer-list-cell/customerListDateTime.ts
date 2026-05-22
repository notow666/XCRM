import dayjs from 'dayjs';

import { FieldTypeEnum } from '@lib/shared/enums/formDesignEnum';

import type { CrmDataTableColumn } from '@/components/pure/crm-table/type';
import type { FormCreateFieldDateType } from '@/components/business/crm-form-create/types';

const BUILTIN_DATETIME_KEYS = new Set(['followTime', 'collectionTime', 'createTime', 'updateTime', 'actualEndTime']);

export function isCustomerListDateTimeColumn(column: CrmDataTableColumn) {
  const key = String(column.key ?? '');
  if (BUILTIN_DATETIME_KEYS.has(key)) return true;
  return column.filedType === FieldTypeEnum.DATE_TIME;
}

export function parseCustomerListDateTimeDisplay(
  value: string | number | null | undefined,
  dateType?: FormCreateFieldDateType
) {
  if (value === undefined || value === null || value === '') {
    return { dateLine: '-', timeLine: '' };
  }

  const text = String(value).trim();
  if (text === '-') {
    return { dateLine: '-', timeLine: '' };
  }

  const datetimeMatch = text.match(/^(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2}:\d{2})$/);
  if (datetimeMatch) {
    return { dateLine: datetimeMatch[1], timeLine: datetimeMatch[2] };
  }

  if (/^\d{4}-\d{2}-\d{2}$/.test(text)) {
    return { dateLine: text, timeLine: '' };
  }

  if (/^\d{4}-\d{2}$/.test(text)) {
    return { dateLine: text, timeLine: '' };
  }

  const d =
    typeof value === 'number' || /^\d+$/.test(text)
      ? dayjs(typeof value === 'number' ? value : Number(text))
      : dayjs(text);
  if (!d.isValid()) {
    return { dateLine: text, timeLine: '' };
  }

  if (dateType === 'month') {
    return { dateLine: d.format('YYYY-MM'), timeLine: '' };
  }
  if (dateType === 'date') {
    return { dateLine: d.format('YYYY-MM-DD'), timeLine: '' };
  }

  const timeLine = d.format('HH:mm:ss');
  if (dateType === 'datetime') {
    return { dateLine: d.format('YYYY-MM-DD'), timeLine };
  }

  if (timeLine !== '00:00:00') {
    return { dateLine: d.format('YYYY-MM-DD'), timeLine };
  }

  return { dateLine: d.format('YYYY-MM-DD'), timeLine: '' };
}
