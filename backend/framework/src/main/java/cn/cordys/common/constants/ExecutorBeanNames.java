package cn.cordys.common.constants;

/**
 * Spring 异步线程池 Bean 名称。
 */
public final class ExecutorBeanNames {

    public static final String MAIN_ASYNC = "threadPoolTaskExecutor";
    public static final String APPLICATION_TASK = "applicationTaskExecutor";
    /** 单次请求内短时并行（统计、导出行构建等） */
    public static final String PARALLEL = "parallelTaskExecutor";
    /** 多租户批量导入 / 修改 / 删除、异步导出、公海分发等长任务 */
    public static final String BATCH = "batchTaskExecutor";
    /** 号码魔方 Python 生成任务编排 */
    public static final String NUMBER_CUBE_GENERATE = "numberCubeGenerateExecutor";
    /** 号码魔方异步打包下载 */
    public static final String NUMBER_CUBE_DOWNLOAD = "numberCubeDownloadExecutor";

    private ExecutorBeanNames() {
    }
}
