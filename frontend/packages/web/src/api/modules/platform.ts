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

export function updatePlatformTenantName(tenantId: string, name: string) {
  return CDR.post({ url: `/platform/admin/tenant/${tenantId}/name`, data: { name } });
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

export interface PlatformOverviewSeriesItem {
  name: string;
  value: number;
}

export interface PlatformOverview {
  tenantTotal: number;
  tenantActive: number;
  tenantFrozen: number;
  tenantStatusSeries: PlatformOverviewSeriesItem[];
  onlineUserTotal: number;
  onlineTenantUserTotal: number;
  onlineMultiDeviceUserCount: number;
  onlinePlatformUserCount: number;
  onlineDataSpecialistUserCount: number;
  onlineByTenantSeries: PlatformOverviewSeriesItem[];
  activeTenantOnlineCoverageSeries: PlatformOverviewSeriesItem[];
}

export function getPlatformOverview() {
  return CDR.get<PlatformOverview>({ url: '/platform/admin/overview' });
}

export interface PlatformSystemAnnouncementItem {
  id: string;
  subject: string;
  content: string;
  operatorId: string;
  createTime: number;
}

export interface PlatformSystemMaintenanceStatus {
  maintenanceMode: boolean;
  onlineUserTotal: number;
  onlineTenantUserTotal: number;
  onlineDataSpecialistUserCount: number;
}

export interface PlatformAnnouncementPageRequest {
  current: number;
  pageSize: number;
}

export function getPlatformSystemMaintenanceStatus() {
  return CDR.get<PlatformSystemMaintenanceStatus>({ url: '/platform/admin/system-maintenance/status' });
}

export function pagePlatformSystemAnnouncements(data: PlatformAnnouncementPageRequest) {
  return CDR.post<CommonList<PlatformSystemAnnouncementItem>>({
    url: '/platform/admin/system-maintenance/announcement/page',
    data,
  });
}

export function publishPlatformSystemAnnouncement(data: { subject: string; content: string }) {
  return CDR.post<PlatformSystemAnnouncementItem>({
    url: '/platform/admin/system-maintenance/announcement',
    data,
  });
}

export function forcePlatformLogoutAll(data?: { graceSeconds?: number }) {
  return CDR.post({ url: '/platform/admin/system-maintenance/force-logout', data: data ?? {} });
}

export function enterPlatformMaintenanceMode() {
  return CDR.post({ url: '/platform/admin/system-maintenance/maintenance/enter' });
}

export function exitPlatformMaintenanceMode() {
  return CDR.post({ url: '/platform/admin/system-maintenance/maintenance/exit' });
}
