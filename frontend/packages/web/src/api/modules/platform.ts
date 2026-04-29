import type { CommonList } from '@lib/shared/models/common';

import CDR from '@/api/http';

export interface PlatformTenantItem {
  tenantId: string;
  code: string;
  name: string;
  status: string;
  orgId?: string;
  dbName: string;
  jdbcUrl: string;
  enabled: boolean;
  createTime: number;
  updateTime: number;
}

export interface PlatformTenantHealth {
  tenantId: string;
  metadataExists: boolean;
  datasourceRegistered: boolean;
  jdbcReachable: boolean;
  migrationVersion: string;
}

export interface PlatformAuditItem {
  id: string;
  operatorId: string;
  action: string;
  tenantId: string;
  result: string;
  detail: string;
  durationMs: number;
  createTime: number;
}

export interface PlatformTenantPageRequest {
  current: number;
  pageSize: number;
  keyword?: string;
}

export interface PlatformAuditPageRequest {
  current: number;
  pageSize: number;
  tenantId?: string;
}

export interface PlatformLoginPayload {
  username: string;
  password: string;
}

export interface TenantProvisionPayload {
  code: string;
  name: string;
  orgId?: string;
  initialUserIds?: string[];
}

export interface PlatformTenantProvisionTask {
  taskId: string;
  tenantId: string;
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED';
  detail: string;
  operatorId: string;
  createTime: number;
  updateTime: number;
}

export function pagePlatformTenants(data: PlatformTenantPageRequest) {
  return CDR.post<CommonList<PlatformTenantItem>>({ url: '/platform/admin/tenant/page', data });
}

export function getPlatformTenant(tenantId: string) {
  return CDR.get<PlatformTenantItem>({ url: `/platform/admin/tenant/${tenantId}` });
}

export function provisionPlatformTenant(data: TenantProvisionPayload) {
  return CDR.post<PlatformTenantProvisionTask>({
    url: '/platform/admin/tenant/provision',
    data,
  });
}

export function getPlatformTenantProvisionTask(taskId: string) {
  return CDR.get<PlatformTenantProvisionTask>({ url: `/platform/admin/tenant/provision/task/${taskId}` });
}

export function updatePlatformTenantStatus(tenantId: string, enabled: boolean) {
  return CDR.post({ url: `/platform/admin/tenant/${tenantId}/status`, params: { enabled } });
}

export function updatePlatformTenantOrgId(tenantId: string, orgId: string) {
  return CDR.post({ url: `/platform/admin/tenant/${tenantId}/org-id`, data: { orgId } });
}

export function getPlatformTenantHealth(tenantId: string) {
  return CDR.get<PlatformTenantHealth>({ url: `/platform/admin/tenant/${tenantId}/health` });
}

export function rerunPlatformTenantMigrate(tenantId: string) {
  return CDR.post({ url: `/platform/admin/tenant/${tenantId}/migrate` });
}

export function pagePlatformAudits(data: PlatformAuditPageRequest) {
  return CDR.post<CommonList<PlatformAuditItem>>({ url: '/platform/admin/audit/page', data });
}

export function platformLogin(data: PlatformLoginPayload) {
  return CDR.post<any>({ url: '/platform/auth/login', data });
}

export function platformLogout() {
  return CDR.get({ url: '/platform/auth/logout' });
}

export function platformIsLogin() {
  return CDR.get<any>({ url: '/platform/auth/is-login' });
}
