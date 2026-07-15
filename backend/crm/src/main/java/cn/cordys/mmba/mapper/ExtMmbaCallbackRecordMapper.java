package cn.cordys.mmba.mapper;

import cn.cordys.mmba.domain.MmbaCallbackRecord;

public interface ExtMmbaCallbackRecordMapper {

    int markSuccessAndClearPayload(MmbaCallbackRecord record);
}
