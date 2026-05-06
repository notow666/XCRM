package cn.cordys.mmba.domain;

import cn.cordys.common.domain.BaseModel;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Table(name = "mmba_media_file")
public class MmbaMediaFile extends BaseModel {
    private String customerId;
    private String auditRecordId;
    private Integer behaviorType;
    private String reqId;
    private String sourceFilePath;
    private String sourceType;
    private String downloadStatus;
    private Integer retryCount;
    private Long lastDownloadTime;
    private String errorMessage;
    private String storageType;
    private String storagePath;
    private String fileName;
    private String fileExt;
    private String mimeType;
    private Long fileSize;
    private String md5;
    private String esId;
    private String deviceId;
    private String imei;
    private String um;
    private String rawData;
}
