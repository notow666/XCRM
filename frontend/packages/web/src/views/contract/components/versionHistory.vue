<template>
  <div class="contract-version-history">
    <n-empty v-if="!historyItems.length" description="暂无历史记录" />
    <div v-for="item in historyItems" :key="item.version.id" class="history-item">
      <div class="flex items-center justify-between gap-[16px]">
        <div class="font-medium text-[var(--text-n1)]">
          {{ getUserName(item.version.submitUser) }} 于 {{ formatTime(item.version.submitTime) }}
          {{ item.version.submitType === 'CREATE' ? '创建' : '编辑' }}了{{ resourceName }}
        </div>
        <div class="flex items-center gap-[8px]">
          <n-tag size="small" :bordered="false">版本 {{ item.version.versionNo }}</n-tag>
          <n-tag size="small" :bordered="false" :type="getStatusType(item.version.approvalStatus)">
            {{ getStatusName(item.version.approvalStatus) }}
          </n-tag>
        </div>
      </div>

      <div v-if="item.version.approvalTime" class="mt-[8px] text-[var(--text-n4)]">
        {{ getUserName(item.version.approvalUser) }} 于 {{ formatTime(item.version.approvalTime) }}
        {{ item.version.approvalStatus === 'APPROVED' ? '审批通过' : '审批不通过' }}
        <span v-if="item.version.approvalOpinion">，审批意见：{{ item.version.approvalOpinion }}</span>
      </div>

      <div v-if="item.changes.length" class="change-table mt-[12px]">
        <div class="change-header">修改字段</div>
        <div class="change-header">编辑前</div>
        <div class="change-header">编辑后</div>
        <template v-for="change in item.changes" :key="change.key">
          <div class="change-cell font-medium">{{ change.label }}</div>
          <div class="change-cell break-all">{{ change.before }}</div>
          <div class="change-cell break-all">{{ change.after }}</div>
        </template>
      </div>
      <div v-else class="mt-[12px] text-[var(--text-n4)]">
        {{ item.version.submitType === 'CREATE' ? '首次创建，完整内容已保存至该版本。' : '本次未检测到字段变化。' }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { NEmpty, NTag } from 'naive-ui';
  import dayjs from 'dayjs';

  import type { ContractVersionHistoryItem } from '@lib/shared/models/contract';

  type HistoryMode = 'contract' | 'paymentRecord';

  interface ChangeValue {
    before?: unknown;
    after?: unknown;
  }

  interface ChangeItem {
    key: string;
    label: string;
    before: string;
    after: string;
  }

  const props = defineProps<{
    mode: HistoryMode;
    versions?: ContractVersionHistoryItem[];
    userNameMap?: Record<string, string>;
  }>();

  const resourceName = computed(() => (props.mode === 'contract' ? '合同' : '回款记录'));

  const contractFieldNames: Record<string, string> = {
    name: '合同名称',
    number: '合同编码',
    customerId: '客户',
    customerNameSnapshot: '客户姓名',
    customerMobileSnapshot: '客户手机号',
    customerSourceSnapshot: '客户来源',
    owner: '客户负责人',
    ownerNameSnapshot: '客户负责人',
    signerId: '签约人',
    signerNameSnapshot: '签约人',
    signerDeptIdSnapshot: '签约人部门',
    signerDeptNameSnapshot: '签约人部门',
    amount: '合同签约金额',
    expectedRepaymentAmount: '合计应回款金额',
    startTime: '合同开始时间',
    endTime: '合同结束时间',
  };

  const paymentFieldNames: Record<string, string> = {
    name: '回款记录名称',
    no: '回款编码',
    contractId: '合同',
    owner: '做单人',
    recordAmount: '合计回款金额',
    totalLoanAmount: '合计放款金额',
    totalCostAmount: '合计成本金额',
    totalMiscFeeAmount: '合计杂费金额',
    totalCommissionAmount: '合计返佣金额',
    totalRevenueAmount: '合计创收金额',
    firstLoanTime: '最早放款时间',
    lastLoanTime: '最晚放款时间',
    firstRepaymentTime: '最早回款时间',
    lastRepaymentTime: '最晚回款时间',
  };

  const productFieldNames: Record<string, string> = {
    loanTime: '放款时间',
    loanAmount: '放款金额',
    repaymentTime: '回款时间',
    repaymentAmount: '回款金额',
    costAmount: '成本金额',
    miscFeeAmount: '杂费金额',
    commissionAmount: '返佣金额',
    revenueFormula: '创收公式',
    revenueFormulaNormalized: '创收公式',
    revenueAmount: '创收金额',
    pointRate: '点位',
    expectedRepaymentAmount: '应回款金额',
  };

  const dateFields = new Set([
    'startTime',
    'endTime',
    'firstLoanTime',
    'lastLoanTime',
    'firstRepaymentTime',
    'lastRepaymentTime',
    'loanTime',
    'repaymentTime',
  ]);

  const redundantFields = new Set(['schemaVersion', 'customerId', 'owner', 'signerId', 'signerDeptIdSnapshot']);

  function parseJson<T>(value?: string): T | undefined {
    if (!value) return undefined;
    try {
      return JSON.parse(value) as T;
    } catch (error) {
      return undefined;
    }
  }

  function formatValue(value: unknown, key?: string): string {
    if (value === null || value === undefined || value === '') return '-';
    if (key && dateFields.has(key) && typeof value === 'number') return dayjs(value).format('YYYY-MM-DD HH:mm:ss');
    if (Array.isArray(value)) {
      return value.length
        ? value.map((item) => (item && typeof item === 'object' ? JSON.stringify(item) : String(item))).join('、')
        : '-';
    }
    if (typeof value === 'object') return JSON.stringify(value);
    return String(value);
  }

  function formatTime(value?: number): string {
    return value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-';
  }

  function getUserName(userId?: string): string {
    if (!userId) return '系统';
    return props.userNameMap?.[userId] ?? userId;
  }

  function getStatusName(status?: string): string {
    const statusNameMap: Record<string, string> = {
      APPROVING: '待审批',
      APPROVED: '已通过',
      UNAPPROVED: '未通过',
    };
    return statusNameMap[status ?? ''] ?? status ?? '-';
  }

  function getStatusType(status?: string): 'default' | 'error' | 'success' | 'warning' {
    if (status === 'APPROVED') return 'success';
    if (status === 'UNAPPROVED') return 'error';
    if (status === 'APPROVING') return 'warning';
    return 'default';
  }

  function formatProduct(value: unknown): string {
    if (!value || typeof value !== 'object') return '-';
    const entries = Object.entries(value as Record<string, unknown>)
      .filter(([key]) => key !== 'revenueFormulaNormalized')
      .map(([key, item]) => `${productFieldNames[key] ?? key}：${formatValue(item, key)}`);
    return entries.length ? entries.join('；') : '-';
  }

  function formatFieldOption(value: unknown, field?: Record<string, any>): string {
    const options = [...(field?.options ?? []), ...(field?.customOptions ?? []), ...(field?.initialOptions ?? [])];
    const values = Array.isArray(value) ? value : [value];
    const labels = values.map((item) => {
      const option = options.find((candidate: any) => candidate.value === item || candidate.id === item);
      return option?.label ?? option?.name ?? item;
    });
    return formatValue(Array.isArray(value) ? labels : labels[0]);
  }

  function toModuleFieldMap(value: unknown): Map<string, unknown> {
    const result = new Map<string, unknown>();
    if (!Array.isArray(value)) return result;
    value.forEach((item) => {
      if (item && typeof item === 'object' && 'fieldId' in item) {
        const field = item as { fieldId: string; fieldValue: unknown };
        result.set(field.fieldId, field.fieldValue);
      }
    });
    return result;
  }

  function buildModuleFieldChanges(change: ChangeValue, fields: Array<Record<string, any>>): ChangeItem[] {
    const beforeMap = toModuleFieldMap(change.before);
    const afterMap = toModuleFieldMap(change.after);
    const fieldIds = new Set([...beforeMap.keys(), ...afterMap.keys()]);
    const result: ChangeItem[] = [];

    fieldIds.forEach((fieldId) => {
      const before = beforeMap.get(fieldId);
      const after = afterMap.get(fieldId);
      if (JSON.stringify(before) === JSON.stringify(after)) return;
      const field = fields.find((item) => item.id === fieldId);
      result.push({
        key: `moduleField-${fieldId}`,
        label: field?.name ?? `自定义字段（${fieldId}）`,
        before: formatFieldOption(before, field),
        after: formatFieldOption(after, field),
      });
    });
    return result;
  }

  function buildProductChanges(change: ChangeValue): ChangeItem[] {
    const beforeRows = Array.isArray(change.before) ? change.before : [];
    const afterRows = Array.isArray(change.after) ? change.after : [];
    const result: ChangeItem[] = [];
    const rowCount = Math.max(beforeRows.length, afterRows.length);

    for (let index = 0; index < rowCount; index += 1) {
      const before = beforeRows[index];
      const after = afterRows[index];
      if (JSON.stringify(before) !== JSON.stringify(after)) {
        result.push({
          key: `product-${index}`,
          label: `产品明细第 ${index + 1} 行`,
          before: formatProduct(before),
          after: formatProduct(after),
        });
      }
    }
    return result;
  }

  function getChanges(version: ContractVersionHistoryItem): Record<string, ChangeValue> {
    const savedChanges = parseJson<Record<string, ChangeValue>>(version.changeSnapshot);
    if (savedChanges || version.submitType === 'CREATE' || version.baseEffectiveVersionId) return savedChanges ?? {};

    // 首次驳回重提尚无生效版本，用前一次提交快照补全历史展示，不改写审批基线。
    const previousVersion = (props.versions ?? [])
      .filter((item) => item.versionNo < version.versionNo)
      .sort((a, b) => b.versionNo - a.versionNo)[0];
    const before = parseJson<Record<string, unknown>>(previousVersion?.valueSnapshot);
    const after = parseJson<Record<string, unknown>>(version.valueSnapshot);
    if (!before || !after) return {};

    const changes: Record<string, ChangeValue> = {};
    const keys = new Set([...Object.keys(before), ...Object.keys(after)]);
    keys.forEach((key) => {
      if (JSON.stringify(before[key]) !== JSON.stringify(after[key])) {
        changes[key] = { before: before[key], after: after[key] };
      }
    });
    return changes;
  }

  function buildChanges(version: ContractVersionHistoryItem): ChangeItem[] {
    const formConfig = parseJson<{ fields?: Array<Record<string, any>> }>(version.formSnapshot);
    const fields = formConfig?.fields ?? [];
    const changes = getChanges(version);
    const result: ChangeItem[] = [];

    Object.entries(changes).forEach(([key, change]) => {
      if (key === 'moduleFields') {
        result.push(...buildModuleFieldChanges(change, fields));
        return;
      }
      if (key === 'products') {
        result.push(...buildProductChanges(change));
        return;
      }
      if (redundantFields.has(key)) return;
      const fieldNames = props.mode === 'contract' ? contractFieldNames : paymentFieldNames;
      result.push({
        key,
        label: fieldNames[key] ?? key,
        before: formatValue(change.before, key),
        after: formatValue(change.after, key),
      });
    });

    return result;
  }

  const historyItems = computed(() =>
    (props.versions ?? []).map((version) => ({
      version,
      changes: buildChanges(version),
    }))
  );
</script>

<style scoped lang="less">
  .contract-version-history {
    min-height: 240px;
  }

  .history-item {
    padding: 16px;
    border: 1px solid var(--text-n8);
    border-radius: var(--border-radius-mini);
    background: var(--text-n10);

    & + & {
      margin-top: 12px;
    }
  }

  .change-table {
    display: grid;
    grid-template-columns: minmax(140px, 0.7fr) minmax(220px, 1fr) minmax(220px, 1fr);
    overflow: hidden;
    border: 1px solid var(--text-n8);
    border-radius: var(--border-radius-mini);
  }

  .change-header,
  .change-cell {
    padding: 10px 12px;
    border-right: 1px solid var(--text-n8);
    border-bottom: 1px solid var(--text-n8);
  }

  .change-header {
    color: var(--text-n2);
    font-weight: 500;
    background: var(--text-n9);
  }

  .change-header:nth-child(3n),
  .change-cell:nth-child(3n) {
    border-right: 0;
  }

  .change-cell:nth-last-child(-n + 3) {
    border-bottom: 0;
  }
</style>
