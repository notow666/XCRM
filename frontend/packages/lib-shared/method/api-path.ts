/**
 * 与 axios baseURL 中 `VITE_API_BASE_URL` 一致（如 `front` → `/front`），
 * 用于 EventSource、<img> 等不经过 axios 的请求。
 */
export function getApiPathPrefix(): string {
  const raw = import.meta.env.VITE_API_BASE_URL as string | undefined;
  if (raw == null || raw === '') return '';
  const trimmed = String(raw).trim().replace(/^['"]|['"]$/g, '');
  if (!trimmed) return '';
  const seg = trimmed.replace(/^\/+|\/+$/g, '');
  return seg ? `/${seg}` : '';
}

/** 为相对路径加上网关前缀；已是 http(s) 或已带前缀则原样返回 */
export function withApiPathPrefix(path: string): string {
  if (!path) return path;
  if (path.startsWith('http://') || path.startsWith('https://')) return path;
  const prefix = getApiPathPrefix();
  const normalized = path.startsWith('/') ? path : `/${path}`;
  if (!prefix) return normalized;
  if (normalized === prefix || normalized.startsWith(`${prefix}/`)) return normalized;
  return `${prefix}${normalized}`;
}
