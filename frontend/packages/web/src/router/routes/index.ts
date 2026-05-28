import { NON_TENANT_ROUTE_MODULE_KEYS } from './tenant-route-utils';
import type { RouteRecordRaw } from 'vue-router';

const modules = import.meta.glob('./modules/*.ts', { eager: true });

function getModuleKey(filePath: string): string {
  const match = filePath.match(/\/modules\/(.+)\.ts$/);
  return match?.[1] ?? '';
}

function formatModules(_modules: Record<string, { default?: RouteRecordRaw | RouteRecordRaw[] }>) {
  const tenantRoutes: RouteRecordRaw[] = [];
  const platformRoutes: RouteRecordRaw[] = [];

  Object.keys(_modules).forEach((key) => {
    const moduleKey = getModuleKey(key);
    const defaultModule = _modules[key].default;
    if (!defaultModule) return;
    const moduleList = Array.isArray(defaultModule) ? [...defaultModule] : [defaultModule];
    if (NON_TENANT_ROUTE_MODULE_KEYS.has(moduleKey)) {
      platformRoutes.push(...moduleList);
    } else {
      tenantRoutes.push(...moduleList);
    }
  });

  return { tenantRoutes, platformRoutes };
}

const { tenantRoutes, platformRoutes } = formatModules(
  modules as Record<string, { default?: RouteRecordRaw | RouteRecordRaw[] }>
);

/** 权限/菜单遍历用（不含租户 path 前缀） */
export const allAppRoutes = [...tenantRoutes, ...platformRoutes] as RouteRecordRaw[];

export { platformRoutes, tenantRoutes };
export default tenantRoutes;
