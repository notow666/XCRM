import type { TableQueryParams } from '@lib/shared/models/common';
import {
  getNumberCubeDownloadCachedUrl,
  getNumberCubeDownloadFileUrl,
  getNumberCubeDownloadProgressUrl,
  getNumberCubeDownloadStartUrl,
  getNumberCubeProgressUrl,
  getNumberCubeTaskDeleteUrl,
  getNumberCubeTaskDetailUrl,
  NumberCubeProgressBatchUrl,
  NumberCubeRegionsUrl,
  NumberCubeSegmentGroupsUrl,
  NumberCubeSegmentsUrl,
  NumberCubeTaskAddUrl,
  NumberCubeTaskDetailSegmentsUrl,
  NumberCubeTaskPageUrl,
} from '@lib/shared/api/requrls/tools/numberCube';
import type {
  NumberCubeDownloadProgress,
  NumberCubeDownloadStartResult,
  NumberCubeMaskMode,
  NumberCubeSegmentGroup,
  NumberCubeSegmentItem,
  NumberCubeSegmentQueryParams,
  NumberCubeTask,
  NumberCubeTaskCreateParams,
  NumberCubeTaskDetail,
  NumberCubeTaskSegmentDetail,
  NumberCubeTaskSegmentDetailParams,
  NumberCubeTaskPageParams,
  NumberCubeTaskProgress,
  NumberCubeTaskProgressBatchParams,
  PhoneSegmentRegionNode,
} from '@lib/shared/models/tools/numberCube';
import type { CommonList } from '@lib/shared/models/common';
import type { CordysAxios } from '@lib/shared/api/http/Axios';

export default function useNumberCubeApi(CDR: CordysAxios) {
  function getNumberCubeTaskPage(data: NumberCubeTaskPageParams) {
    return CDR.post<CommonList<NumberCubeTask>>({ url: NumberCubeTaskPageUrl, data });
  }

  function addNumberCubeTask(data: NumberCubeTaskCreateParams) {
    return CDR.post<NumberCubeTask>({ url: NumberCubeTaskAddUrl, data });
  }

  function getNumberCubeTaskDetail(id: string) {
    return CDR.get<NumberCubeTaskDetail>({ url: getNumberCubeTaskDetailUrl(id) });
  }

  function getNumberCubeTaskDetailSegments(data: NumberCubeTaskSegmentDetailParams) {
    return CDR.post<NumberCubeTaskSegmentDetail>({ url: NumberCubeTaskDetailSegmentsUrl, data });
  }

  function deleteNumberCubeTask(id: string) {
    return CDR.post({ url: getNumberCubeTaskDeleteUrl(id) });
  }

  function getNumberCubeRegions() {
    return CDR.get<PhoneSegmentRegionNode[]>({ url: NumberCubeRegionsUrl });
  }

  function getNumberCubeSegmentGroups(data: NumberCubeSegmentQueryParams) {
    return CDR.post<NumberCubeSegmentGroup[]>({ url: NumberCubeSegmentGroupsUrl, data });
  }

  function getNumberCubeSegments(data: NumberCubeSegmentQueryParams) {
    return CDR.post<NumberCubeSegmentItem[]>({ url: NumberCubeSegmentsUrl, data });
  }

  function getNumberCubeTaskProgress(id: string) {
    return CDR.get<NumberCubeTaskProgress>({ url: getNumberCubeProgressUrl(id) });
  }

  function getNumberCubeTaskProgressBatch(data: NumberCubeTaskProgressBatchParams) {
    return CDR.post<NumberCubeTaskProgress[]>({ url: NumberCubeProgressBatchUrl, data });
  }

  function startNumberCubeDownload(id: string, maskMode: NumberCubeMaskMode) {
    return CDR.post<NumberCubeDownloadStartResult>({ url: getNumberCubeDownloadStartUrl(id, maskMode) });
  }

  function getNumberCubeDownloadProgress(exportJobId: string, taskId: string) {
    return CDR.get<NumberCubeDownloadProgress>({ url: getNumberCubeDownloadProgressUrl(exportJobId, taskId) });
  }

  function downloadNumberCubeFile(exportJobId: string, taskId: string) {
    return CDR.get(
      { url: getNumberCubeDownloadFileUrl(exportJobId, taskId), responseType: 'blob' },
      { isTransformResponse: false }
    );
  }

  function downloadNumberCubeCachedFile(id: string, maskMode: NumberCubeMaskMode) {
    return CDR.get(
      { url: getNumberCubeDownloadCachedUrl(id, maskMode), responseType: 'blob' },
      { isTransformResponse: false }
    );
  }

  return {
    getNumberCubeTaskPage,
    addNumberCubeTask,
    getNumberCubeTaskDetail,
    getNumberCubeTaskDetailSegments,
    deleteNumberCubeTask,
    getNumberCubeRegions,
    getNumberCubeSegmentGroups,
    getNumberCubeSegments,
    getNumberCubeTaskProgress,
    getNumberCubeTaskProgressBatch,
    startNumberCubeDownload,
    getNumberCubeDownloadProgress,
    downloadNumberCubeFile,
    downloadNumberCubeCachedFile,
  };
}
