import CDR from '@/api/http';

export interface BlacklistRow {
  id: string;
  mobile: string;
  customerName: string | null;
}
export interface BlacklistResult {
  successCount: number;
  failCount: number;
  errorFileId?: string;
  errorFileName?: string;
}

export const blacklistPage = (keyword: string, current: number, pageSize: number) =>
  CDR.post<{ list: BlacklistRow[]; total: number }>({ url: '/blacklist/page', data: { keyword, current, pageSize } });
export const addBlacklist = (data: { mobile: string; customerName: string }) =>
  CDR.post<BlacklistResult>({ url: '/blacklist/add', data });
export const deleteBlacklist = (ids: string[]) =>
  CDR.post<BlacklistResult>({ url: '/blacklist/batch-delete', data: { ids } });
export const deleteBlacklistByCondition = (keyword: string) =>
  CDR.post<BlacklistResult>({ url: '/blacklist/delete-by-condition', data: { keyword }, timeout: 300000 });
export const exportBlacklist = (keyword: string, ids: string[]) =>
  CDR.post<Blob>(
    { url: '/blacklist/export', data: { keyword, ids }, timeout: 300000, responseType: 'blob' },
    { isTransformResponse: false }
  );
export async function importBlacklist(file: File) {
  const response = await CDR.uploadFile<{ data: BlacklistResult }>(
    { url: '/blacklist/import', timeout: 300000 },
    { fileList: [file] }
  );
  return response.data;
}
export const downloadBlacklistFile = (id?: string) =>
  CDR.get<Blob>(
    { url: id ? `/blacklist/import-error/${encodeURIComponent(id)}` : '/blacklist/template', responseType: 'blob' },
    { isTransformResponse: false }
  );
