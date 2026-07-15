import type { TableQueryParams } from '@lib/shared/models/common';



export interface PhoneSegmentRegionNode {

  label: string;

  value: string;

  children?: PhoneSegmentRegionNode[];

}



export interface NumberCubeSegmentItem {

  id: string;

  province: string;

  city: string;

  segment: string;

  isp?: string;

  areaCode?: string;

}



export interface NumberCubeTaskPageParams extends TableQueryParams {

  province?: string;

  city?: string;

  createTimeStart?: number;

  createTimeEnd?: number;

}



export interface NumberCubeTaskCreateParams {

  province: string;

  city: string;

  selectionMode?: 'ALL' | 'PARTIAL';

  segmentIds?: string[];

}



export interface NumberCubeSegmentQueryParams {

  province: string;

  city: string;

  prefix?: string;

}



export interface NumberCubeSegmentGroup {

  prefix: string;

  count: number;

}



export const NUMBER_CUBE_NUMBERS_PER_SEGMENT = 10000;



export type NumberCubeMaskMode = 'PLAIN' | 'MASKED';



export interface NumberCubeTaskProgress {

  jobId: string;

  status: string;

  total?: number;

  processed?: number;

  generated?: number;

  skipped?: number;

  failed?: number;

}



export interface NumberCubeTask {
  id: string;
  province: string;
  city: string;
  segmentCount?: number;
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | string;
  errorMessage?: string;
  plainPackFileId?: string;
  maskedPackFileId?: string;
  plainPackReady?: boolean;
  maskedPackReady?: boolean;
  createTime?: number;
  updateTime?: number;
}

export interface NumberCubeTaskDetail {
  id: string;
  province: string;
  city: string;
  selectionMode?: 'ALL' | 'PARTIAL' | string;
  segmentCount?: number;
  segmentGroups?: NumberCubeSegmentGroup[];
  status: string;
  errorMessage?: string;
  createTime?: number;
  updateTime?: number;
}

export interface NumberCubeTaskSegmentDetailParams {
  taskId: string;
  prefix: string;
}

export interface NumberCubeTaskSegmentDetail {
  taskId: string;
  prefix: string;
  segments: string[];
}

export interface NumberCubeTaskProgressBatchParams {
  taskIds: string[];
}

export interface NumberCubeDownloadStartResult {
  exportJobId?: string;
  status: string;
  cached?: boolean;
  fileId?: string;
}

export interface NumberCubeDownloadProgress {
  exportJobId: string;
  status: string;
  total?: number;
  processed?: number;
  errorMessage?: string;
  fileName?: string;
}



export interface PlatformPhoneSegmentItem {

  id: string;

  province: string;

  city: string;

  segment: string;

  isp?: string;

  areaCode?: string;

}



export interface PlatformPhoneSegmentPageParams extends TableQueryParams {

  province?: string;

  city?: string;

}

