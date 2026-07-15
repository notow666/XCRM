export const NumberCubeTaskPageUrl = '/tools/number-cube/page';
export const NumberCubeTaskAddUrl = '/tools/number-cube/add';
export const getNumberCubeTaskDetailUrl = (id: string) => `/tools/number-cube/detail/${id}`;
export const NumberCubeTaskDetailSegmentsUrl = '/tools/number-cube/detail/segments';
export const getNumberCubeTaskDeleteUrl = (id: string) => `/tools/number-cube/delete/${id}`;
export const NumberCubeRegionsUrl = '/tools/number-cube/regions';
export const NumberCubeSegmentsUrl = '/tools/number-cube/segments';
export const NumberCubeSegmentGroupsUrl = '/tools/number-cube/segments/groups';
export const NumberCubeProgressBatchUrl = '/tools/number-cube/progress/batch';
export const getNumberCubeProgressUrl = (id: string) => `/tools/number-cube/progress/${id}`;
export const getNumberCubeDownloadStartUrl = (id: string, maskMode: 'PLAIN' | 'MASKED') =>
  `/tools/number-cube/download/start/${id}?maskMode=${maskMode}`;
export const getNumberCubeDownloadProgressUrl = (exportJobId: string, taskId: string) =>
  `/tools/number-cube/download/progress/${exportJobId}?taskId=${encodeURIComponent(taskId)}`;
export const getNumberCubeDownloadFileUrl = (exportJobId: string, taskId: string) =>
  `/tools/number-cube/download/file/${exportJobId}?taskId=${encodeURIComponent(taskId)}`;
export const getNumberCubeDownloadCachedUrl = (id: string, maskMode: 'PLAIN' | 'MASKED') =>
  `/tools/number-cube/download/cached/${id}?maskMode=${maskMode}`;
