import type { CommonList } from '@lib/shared/models/common';
import type { PoolCustomerImportCheckResponse } from '@lib/shared/models/customer';

import CDR from '@/api/http';

export interface DataSpecialistTenantItem {
  tenantId: string;
  code: string;
  name: string;
}

export interface DataSpecialistAdminPageRequest {
  current: number;
  pageSize: number;
  keyword?: string;
}

export interface DataSpecialistAdminItem {
  id: string;
  username: string;
  enabled: boolean;
  createTime: number;
  updateTime: number;
}

export interface DataSpecialistAdminDetail extends DataSpecialistAdminItem {
  tenantIds: string[];
}

export interface DataSpecialistCreatePayload {
  username: string;
  password: string;
  tenantIds: string[];
}

export interface DataSpecialistUpdatePayload {
  password?: string;
  enabled?: boolean;
  tenantIds?: string[];
}

/** 租户上下文由 {@link TenantContextWebFilter} 从 query 或 X-Tenant-ID 解析，上传类接口统一走 query tenantId。 */
const tenantParams = (tenantId: string) => ({ tenantId });

export function dataSpecialistLogin(data: { username: string; password: string }) {
  return CDR.post<any>({ url: '/data-specialist/auth/login', data });
}

export function dataSpecialistLogout() {
  return CDR.get({ url: '/data-specialist/auth/logout' });
}

export function dataSpecialistIsLogin() {
  return CDR.get<any>({ url: '/data-specialist/auth/is-login' });
}

export function dataSpecialistListTenants() {
  return CDR.get<DataSpecialistTenantItem[]>({ url: '/data-specialist/tenants' });
}

export function dataSpecialistListPools(tenantId: string) {
  return CDR.get<any[]>({ url: '/data-specialist/pools', params: tenantParams(tenantId) });
}

export function dataSpecialistDownloadPoolTemplate(tenantId: string) {
  return CDR.get(
    {
      url: '/data-specialist/pool/import/template/download',
      responseType: 'blob',
      params: tenantParams(tenantId),
    },
    { isTransformResponse: false, isReturnNativeResponse: true }
  );
}

export function dataSpecialistPreCheckPoolImport(tenantId: string, file: File, poolId: string) {
  return CDR.uploadFile<{ data: PoolCustomerImportCheckResponse }>(
    { url: '/data-specialist/pool/import/pre-check', params: { poolId, tenantId } },
    { fileList: [file] },
    'file'
  );
}

export function dataSpecialistImportPool(tenantId: string, file: File, poolId: string) {
  return CDR.uploadFile(
    { url: '/data-specialist/pool/import', params: { poolId, tenantId } },
    { fileList: [file] },
    'file'
  );
}

export function dataSpecialistDownloadPoolErrorFile(tenantId: string, fileId: string) {
  return CDR.get(
    {
      url: `/data-specialist/pool/import/error-file/${fileId}`,
      responseType: 'blob',
      params: tenantParams(tenantId),
    },
    { isTransformResponse: false, isReturnNativeResponse: true }
  );
}

/** 管理中心：数据专员分页 */
export function pageDataSpecialists(data: DataSpecialistAdminPageRequest) {
  return CDR.post<CommonList<DataSpecialistAdminItem>>({ url: '/platform/admin/data-specialist/page', data });
}

export function getDataSpecialist(id: string) {
  return CDR.get<DataSpecialistAdminDetail>({ url: `/platform/admin/data-specialist/${id}` });
}

export function createDataSpecialist(data: DataSpecialistCreatePayload) {
  return CDR.post<string>({ url: '/platform/admin/data-specialist', data });
}

export function updateDataSpecialist(id: string, data: DataSpecialistUpdatePayload) {
  return CDR.put({ url: `/platform/admin/data-specialist/${id}`, data });
}
