import type { CommonList } from '@lib/shared/models/common';

import CDR from '@/api/http';

export interface PlatformUserAdminPageRequest {
  current: number;
  pageSize: number;
  keyword?: string;
}

export interface PlatformUserAdminItem {
  id: string;
  username: string;
  nickname?: string | null;
  status: string;
  createTime: number;
  updateTime: number;
}

export type PlatformUserAdminDetail = PlatformUserAdminItem;

export interface PlatformUserCreatePayload {
  username: string;
  nickname?: string;
  password: string;
}

export interface PlatformUserUpdatePayload {
  nickname?: string;
  password?: string;
  status?: string;
}

export function pagePlatformUsers(data: PlatformUserAdminPageRequest) {
  return CDR.post<CommonList<PlatformUserAdminItem>>({ url: '/platform/admin/platform-user/page', data });
}

export function getPlatformUser(id: string) {
  return CDR.get<PlatformUserAdminDetail>({ url: `/platform/admin/platform-user/${id}` });
}

export function createPlatformUser(data: PlatformUserCreatePayload) {
  return CDR.post<string>({ url: '/platform/admin/platform-user', data });
}

export function updatePlatformUser(id: string, data: PlatformUserUpdatePayload) {
  return CDR.put({ url: `/platform/admin/platform-user/${id}`, data });
}
