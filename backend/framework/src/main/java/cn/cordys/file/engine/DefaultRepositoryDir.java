package cn.cordys.file.engine;

import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 默认的资源目录工具类；逻辑路径首段为租户 ID。调用方必须传入非空的 tenantId，禁止静默回退到默认租户。
 */
public final class DefaultRepositoryDir {

    private static final String DEFAULT_REPOSITORY_ROOT = "/opt/cordys/data/files";
    private static volatile String repositoryRoot = DEFAULT_REPOSITORY_ROOT;

    private static final String TMP_SEGMENT = "tmp";
    private static final String EXPORT_SEGMENT = "export";
    private static final String PIC_SEGMENT = "pic";

    private DefaultRepositoryDir() {
    }

    /**
     * 由 Spring 在启动时注入；未调用前使用 {@link #DEFAULT_REPOSITORY_ROOT}。
     */
    public static void setRepositoryRoot(String root) {
        if (StringUtils.isBlank(root)) {
            return;
        }
        repositoryRoot = root.trim();
    }

    /**
     * 临时根目录（相对仓库根的逻辑路径，以 / 开头）：/{tenantId}/tmp
     */
    public static String getTmpDir(String tenantId) {
        return "/" + normalizeTenant(tenantId) + "/" + TMP_SEGMENT;
    }

    /**
     * 临时文件目录：/{tenantId}/tmp/{tempFileId}
     */
    public static String getTempFileDir(String tenantId, String tempFileId) {
        return getTmpDir(tenantId) + "/" + tempFileId;
    }

    /**
     * 导出目录相对段（无首斜杠，便于与 {@link java.io.File} 拼接）：{tenantId}/export
     */
    public static String getExportDir(String tenantId) {
        return Paths.get(normalizeTenant(tenantId), EXPORT_SEGMENT).toString();
    }

    /**
     * 转存附件目录：/{tenantId}/pic/{resourceId}/{fileId}
     */
    public static String getTransferFileDir(String tenantId, String resourceId, String fileId) {
        return "/" + normalizeTenant(tenantId) + "/" + PIC_SEGMENT + "/" + resourceId + "/" + fileId;
    }

    public static String getDefaultDir() {
        return repositoryRoot;
    }

    public static Path getFullTmpPath(String tenantId) {
        return Paths.get(repositoryRoot, normalizeTenant(tenantId), TMP_SEGMENT);
    }

    public static Path getFullPicPath(String tenantId) {
        return Paths.get(repositoryRoot, normalizeTenant(tenantId), PIC_SEGMENT);
    }

    public static Path getFullExportPath(String tenantId) {
        return Paths.get(repositoryRoot, normalizeTenant(tenantId), EXPORT_SEGMENT);
    }

    private static String normalizeTenant(String tenantId) {
        String t = StringUtils.trimToNull(tenantId);
        if (t == null) {
            throw new IllegalArgumentException("tenantId must not be null or blank for file repository paths");
        }
        if (t.indexOf('/') >= 0 || t.indexOf('\\') >= 0 || t.contains("..")) {
            throw new IllegalArgumentException("Invalid tenantId: " + tenantId);
        }
        return t;
    }
}
