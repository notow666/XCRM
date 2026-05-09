import type { CordysAxios } from '@lib/shared/api/http/Axios';
import {
  MmbaDeviceAddUrl,
  MmbaDeviceImportUrl,
  MmbaDeviceListOptionsUrl,
  MmbaDevicePageUrl,
  MmbaDeviceSyncUrl,
  MmbaDeviceUpdateUrl,
  getMmbaDeviceDetailUrl,
} from '@lib/shared/api/requrls/mmba/device';
import type { CommonList } from '@lib/shared/models/common';
import type {
  MmbaDevice,
  MmbaDeviceImportResult,
  MmbaDevicePageParams,
  MmbaDeviceSaveParams,
  MmbaDeviceUpdateParams,
} from '@lib/shared/models/mmba/device';
import type { OptionDTO } from '@lib/shared/models/system/business';

export default function useMmbaDeviceApi(CDR: CordysAxios) {
  function getMmbaDevicePage(data: MmbaDevicePageParams) {
    return CDR.post<CommonList<MmbaDevice>>({ url: MmbaDevicePageUrl, data });
  }

  function getMmbaDeviceDetail(id: string) {
    return CDR.get<MmbaDevice>({ url: getMmbaDeviceDetailUrl(id) });
  }

  function addMmbaDevice(data: MmbaDeviceSaveParams) {
    return CDR.post<MmbaDevice>({ url: MmbaDeviceAddUrl, data });
  }

  function updateMmbaDevice(data: MmbaDeviceUpdateParams) {
    return CDR.post<MmbaDevice>({ url: MmbaDeviceUpdateUrl, data });
  }

  function importMmbaDevice(file: File) {
    return CDR.uploadFile<{ data: MmbaDeviceImportResult }>(
      { url: MmbaDeviceImportUrl },
      { fileList: [file] },
      'file'
    ).then((body) => body.data);
  }

  function getMmbaDeviceOptionList() {
    return CDR.get<OptionDTO[]>({ url: MmbaDeviceListOptionsUrl });
  }

  function syncMmbaDevices() {
    return CDR.post<void>({ url: MmbaDeviceSyncUrl, data: {} });
  }

  return {
    getMmbaDevicePage,
    getMmbaDeviceDetail,
    addMmbaDevice,
    updateMmbaDevice,
    importMmbaDevice,
    getMmbaDeviceOptionList,
    syncMmbaDevices,
  };
}
