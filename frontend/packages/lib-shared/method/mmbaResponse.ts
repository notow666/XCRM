/** MMBA 网关成功码，与后端 {@link cn.cordys.mmba.MmbaGatewayService} 一致 */
const MMBA_SUCCESS_CODES = new Set([200, 201]);

/**
 * 校验 MMBA 业务响应体；无 code 字段时视为已通过 CRM 网关（兼容非标准体）。
 */
export function assertMmbaResponseSuccess(response: unknown): void {
  if (response == null || typeof response !== 'object') {
    return;
  }
  const body = response as { code?: number; message?: string };
  if (body.code === undefined || body.code === null) {
    return;
  }
  const code = Number(body.code);
  if (MMBA_SUCCESS_CODES.has(code)) {
    return;
  }
  throw new Error(body.message?.trim() || 'MMBA request failed');
}
