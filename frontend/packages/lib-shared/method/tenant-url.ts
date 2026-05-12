import { getLocalStorage } from './local-storage';

/**
 * 与 axios 拦截器一致：优先从 hash 登录路由解析租户，否则从持久化的 app 状态读取。
 * 用于 img 标签等无法带请求头的场景，拼到 URL 查询参数 `tenantId`。
 */
export function resolveClientTenantId(): string {
  if (typeof window === 'undefined') {
    return '';
  }
  const hash = window.location.hash || '';
  const match = hash.match(/^#\/([^/]+)\/login(?:\?|$)/);
  if (match && typeof match[1] === 'string' && match[1].trim()) {
    return decodeURIComponent(match[1].trim());
  }
  const app = getLocalStorage<Record<string, unknown>>('app', true);
  const id = app?.tenantId;
  return typeof id === 'string' && id.trim() ? id.trim() : '';
}

/** 在 URL 上追加 `tenantId`（匿名预览等）；无租户则返回原 path */
export function appendTenantQuery(path: string, tenantId?: string): string {
  const tid = (tenantId ?? resolveClientTenantId()).trim();
  if (!tid) {
    return path;
  }
  const sep = path.includes('?') ? '&' : '?';
  return `${path}${sep}tenantId=${encodeURIComponent(tid)}`;
}
