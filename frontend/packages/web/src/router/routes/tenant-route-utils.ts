import {
  isUsableBusinessTenantId,
  normalizeTenantId,
  resolvePersistedAppTenantIdForRedirect,
} from '@lib/shared/method/tenant-url';

import type { RouteRecordRaw } from 'vue-router';

/** 不参与租户前缀的平台模块（文件名） */
export const NON_TENANT_ROUTE_MODULE_KEYS = new Set(['management-center']);

/** 旧版无租户前缀的顶层 path，用于兼容重定向 */
export const LEGACY_TENANT_ROUTE_ROOTS = [
  'workbench',
  'account',
  'lead',
  'contract',
  'order',
  'task',
  'product',
  'opportunity',
  'dashboard',
  'agent',
  'tender',
  'report',
  'system',
  'fullPage',
  'mmba-audit',
  'noResource',
] as const;

/** 旧 URL 仅访问模块根路径时的默认子路径（与原 redirect 一致，去掉 leading `/`） */
export const LEGACY_ROUTE_DEFAULT_SUFFIX: Record<string, string> = {
  workbench: 'workbench/index',
  account: 'account/index',
  lead: 'lead/index',
  contract: 'contract/index',
  order: 'order/index',
  task: 'task/index',
  product: 'product/pro',
  opportunity: 'opportunity/opt',
  dashboard: 'dashboard/index',
  agent: 'agent/index',
  tender: 'tender/index',
  report: 'report/employee/follow-up-analysis',
  system: 'system/role',
  fullPage: 'fullPage/fullPageDashboard',
  'mmba-audit': 'mmba-audit/mgmt-sso',
  noResource: 'noResource/index',
};

function stripLeadingSlash(path: string): string {
  return path.startsWith('/') ? path.slice(1) : path;
}

function formatPathMatch(pathMatch: string | string[] | undefined): string {
  if (pathMatch == null || pathMatch === '') {
    return '';
  }
  return Array.isArray(pathMatch) ? pathMatch.join('/') : pathMatch;
}

export function redirectWithTenantPrefix(pathWithoutTenant: string): string {
  const tenantId = resolvePersistedAppTenantIdForRedirect();
  const normalized = pathWithoutTenant.startsWith('/') ? pathWithoutTenant : `/${pathWithoutTenant}`;
  if (!tenantId) {
    return '/platform/login';
  }
  return `/${tenantId}${normalized}`;
}

/** 将模块绝对 path（/workbench）改为相对 path（workbench），以便挂在 /:tenantId 下 */
export function prefixRoutesForTenantScope(routes: RouteRecordRaw[]): RouteRecordRaw[] {
  return routes.map((route) => {
    const r: RouteRecordRaw = { ...route };
    if (typeof r.path === 'string' && r.path.startsWith('/')) {
      r.path = stripLeadingSlash(r.path);
    }
    if (typeof r.redirect === 'string' && r.redirect.startsWith('/')) {
      r.redirect = stripLeadingSlash(r.redirect);
    }
    if (r.children?.length) {
      r.children = prefixRoutesForTenantScope(r.children);
    }
    return r;
  });
}

export function createLegacyTenantRedirects(): RouteRecordRaw[] {
  const routes: RouteRecordRaw[] = [];

  LEGACY_TENANT_ROUTE_ROOTS.forEach((root) => {
    routes.push({
      path: `/${root}`,
      redirect: () => redirectWithTenantPrefix(`/${LEGACY_ROUTE_DEFAULT_SUFFIX[root] ?? `${root}/index`}`),
    });
    routes.push({
      path: `/${root}/:pathMatch(.*)*`,
      redirect: (to) => {
        const sub = formatPathMatch(to.params.pathMatch as string | string[] | undefined);
        const suffix = sub ? `/${root}/${sub}` : `/${LEGACY_ROUTE_DEFAULT_SUFFIX[root] ?? `${root}/index`}`;
        return redirectWithTenantPrefix(suffix);
      },
    });
  });

  return routes;
}

/** 路由是否落在 /:tenantId/* 租户域内 */
export function isTenantScopedRoute(path: string, params: Record<string, unknown>): boolean {
  const tenantId = normalizeTenantId(params.tenantId);
  if (!isUsableBusinessTenantId(tenantId)) {
    return false;
  }
  return path === `/${tenantId}` || path.startsWith(`/${tenantId}/`);
}
