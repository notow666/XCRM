import { hasToken } from './auth';
import { getLocalStorage } from './local-storage';

/** 平台内置占位租户，不作为业务用户会话失效时的隐式回退目标 */
export const RESERVED_DEFAULT_TENANT_ID = 'default';

/** hash 首段为平台/专员等全局路由时，不作为租户 ID */
export const NON_TENANT_HASH_ROOTS = new Set([
  'platform',
  'data-specialist',
  'management-center',
  'notFound',
]);

export function normalizeTenantId(value: unknown): string {
  return typeof value === 'string' ? value.trim() : '';
}

/** 是否可作为业务租户登录/重定向目标（排除空与 default 占位） */
export function isUsableBusinessTenantId(tenantId: string): boolean {
  const id = normalizeTenantId(tenantId);
  return id.length > 0 && id !== RESERVED_DEFAULT_TENANT_ID && !NON_TENANT_HASH_ROOTS.has(id);
}

export type ResolveTenantIdForAuthRedirectOptions = {
  /** 当前路由 params.tenantId（显式路径优先，含 /default/login） */
  routeTenantId?: unknown;
  /** pinia user.userInfo.tenantId */
  userTenantId?: unknown;
  /** pinia app.tenantId */
  appTenantId?: unknown;
  /**
   * 已登录场景：优先会话租户，避免手改 hash 后 API 头跟随 URL 切库。
   * 未登录/登出重定向保持默认 false（路由 param 优先）。
   */
  sessionFirst?: boolean;
};

function resolvePersistedUserTenantId(): string {
  try {
    const raw = localStorage.getItem('user');
    if (!raw) {
      return '';
    }
    const data = JSON.parse(raw) as { userInfo?: { tenantId?: unknown; source?: unknown } };
    const source = typeof data?.userInfo?.source === 'string' ? data.userInfo.source : '';
    if (source === 'PLATFORM' || source === 'DATA_SPECIALIST') {
      return '';
    }
    return normalizeTenantId(data?.userInfo?.tenantId);
  } catch {
    return '';
  }
}

/** 当前登录会话绑定的租户 ID（含 default；平台/专员返回空） */
export function getSessionTenantId(): string {
  if (!hasToken()) {
    return '';
  }
  return resolvePersistedUserTenantId();
}

/** 路由 params.tenantId 与会话租户不一致（已登录租户用户） */
export function isTenantRouteMismatch(routeTenantId: unknown, sessionTenantId?: string): boolean {
  const session = sessionTenantId ?? getSessionTenantId();
  if (!session) {
    return false;
  }
  const route = normalizeTenantId(routeTenantId);
  if (!route) {
    return false;
  }
  return route !== session;
}

/**
 * 会话失效/登出后解析应跳转的租户 ID。
 * 默认顺序：路由 param → userInfo → appStore → hash / localStorage app。
 * sessionFirst=true：userInfo → appStore → 路由 → hash（已登录导航/请求头用）。
 * 隐式来源中的 `default` 视为污染占位，不采用；路由/会话显式带 default 时仍保留。
 */
export function resolveTenantIdForAuthRedirect(
  options: ResolveTenantIdForAuthRedirectOptions = {}
): string {
  if (options.sessionFirst) {
    const userId = normalizeTenantId(options.userTenantId);
    if (userId) {
      return userId;
    }
    const appId = normalizeTenantId(options.appTenantId);
    if (isUsableBusinessTenantId(appId)) {
      return appId;
    }
    const routeId = normalizeTenantId(options.routeTenantId);
    if (routeId) {
      return routeId;
    }
    const clientId = resolveClientTenantIdUnauthenticated();
    if (isUsableBusinessTenantId(clientId)) {
      return clientId;
    }
    return '';
  }

  const routeId = normalizeTenantId(options.routeTenantId);
  if (routeId) {
    return routeId;
  }

  const userId = normalizeTenantId(options.userTenantId);
  if (isUsableBusinessTenantId(userId)) {
    return userId;
  }

  const appId = normalizeTenantId(options.appTenantId);
  if (isUsableBusinessTenantId(appId)) {
    return appId;
  }

  const clientId = resolveClientTenantIdUnauthenticated();
  if (isUsableBusinessTenantId(clientId)) {
    return clientId;
  }

  return '';
}

/**
 * 从 hash 路径解析租户：/#/{tenantId}/workbench/... 首段即为 tenantId。
 */
export function resolveTenantIdFromHashPath(): string {
  if (typeof window === 'undefined') {
    return '';
  }
  const hash = window.location.hash || '';
  const pathOnly = hash.replace(/^#/, '').split('?')[0] || '';
  const segments = pathOnly.split('/').filter(Boolean);
  if (!segments.length) {
    return '';
  }
  const first = decodeURIComponent(segments[0]);
  if (NON_TENANT_HASH_ROOTS.has(first)) {
    return '';
  }
  return first;
}

/** 未登录时：hash 路径优先，否则持久化 app.tenantId */
function resolveClientTenantIdUnauthenticated(): string {
  const fromHash = resolveTenantIdFromHashPath();
  if (isUsableBusinessTenantId(fromHash)) {
    return fromHash;
  }
  const app = getLocalStorage<Record<string, unknown>>('app', true);
  const id = app?.tenantId;
  const persisted = typeof id === 'string' && id.trim() ? id.trim() : '';
  return isUsableBusinessTenantId(persisted) ? persisted : '';
}

/**
 * 与 axios 拦截器一致：已登录优先会话租户，未登录从 hash / app 解析。
 * 用于 img 标签等无法带请求头的场景，拼到 URL 查询参数 `tenantId`。
 */
export function resolveClientTenantId(): string {
  const sessionTenantId = getSessionTenantId();
  if (sessionTenantId) {
    return sessionTenantId;
  }
  return resolveClientTenantIdUnauthenticated();
}

/** 根路由 `/` 等场景：从持久化 app 解析租户，忽略 default 占位 */
export function resolvePersistedAppTenantIdForRedirect(): string {
  if (typeof window === 'undefined') {
    return '';
  }
  try {
    const appRaw = localStorage.getItem('app');
    if (!appRaw) {
      return '';
    }
    const app = JSON.parse(appRaw) as { tenantId?: unknown };
    const tenantId = normalizeTenantId(app?.tenantId);
    return isUsableBusinessTenantId(tenantId) ? tenantId : '';
  } catch {
    return '';
  }
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
