package cn.cordys.crm.tools.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "cordys.number-cube")
public class NumberCubeProperties {

    private String pythonPath = "python";

    private String generateScriptPath = "scripts/generate_number_cube_segments.py";

    private String packScriptPath = "scripts/pack_number_cube_export.py";

    private int pythonWorkers = 8;

    private int generateBatchSize = 20;

    private int maxTasksPerChild = 50;

    private int maxConcurrentGenerateJobs = 2;

    private int maxConcurrentDownloads = 2;

    private int rowsPerExcel = 1_000_000;

    private int maxSegmentsPerTask = 25_000;

    private int generateTimeoutHours = 4;

    private boolean callbackLocalOnly = true;

    private int callbackPort = 8081;
}
