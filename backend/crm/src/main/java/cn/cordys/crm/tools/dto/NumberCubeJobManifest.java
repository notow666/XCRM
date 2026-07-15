package cn.cordys.crm.tools.dto;

import lombok.Data;

@Data
public class NumberCubeJobManifest {

    private String jobId;

    private String tenantId;

    private String organizationId;

    private String province;

    private String city;

    private String segmentsFile;

    private Integer segmentCount;

    private String cubeRoot;

    private String jobsDir;

    private String callbackUrl;

    private String callbackToken;

    private int workers;

    private Integer batchSize;

    private Integer maxTasksPerChild;
}
