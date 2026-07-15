package cn.cordys.platform.util;

import cn.cordys.file.engine.DefaultRepositoryDir;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

public final class NumberCubePathUtils {

    public static final String PLAIN_FILE_NAME = "plain.csv";
    public static final String JOBS_DIR = "jobs";
    public static final String MANIFEST_SUFFIX = ".manifest.json";
    public static final String STATUS_SUFFIX = ".status.json";
    public static final String SEGMENTS_SUFFIX = ".segments.txt";
    public static final String LOG_SUFFIX = ".log";

    private static final Pattern UNSAFE_PATH = Pattern.compile("[\\\\/:*?\"<>|]");

    private NumberCubePathUtils() {
    }

    public static Path getMasterCubeRoot() {
        return Paths.get(DefaultRepositoryDir.getDefaultDir(), "master", "cube");
    }

    public static Path getJobsDir() {
        return getMasterCubeRoot().resolve(JOBS_DIR);
    }

    public static Path getJobManifestPath(String jobId) {
        return getJobsDir().resolve(safeFileName(jobId) + MANIFEST_SUFFIX);
    }

    public static Path getJobStatusPath(String jobId) {
        return getJobsDir().resolve(safeFileName(jobId) + STATUS_SUFFIX);
    }

    public static Path getJobSegmentsPath(String jobId) {
        return getJobsDir().resolve(safeFileName(jobId) + SEGMENTS_SUFFIX);
    }

    public static Path getJobLogPath(String jobId) {
        return getJobsDir().resolve(safeFileName(jobId) + LOG_SUFFIX);
    }

    public static Path getSegmentDir(String province, String city, String segment) {
        String prefix = segmentPrefix(segment);
        return getMasterCubeRoot()
                .resolve(safePathSegment(province))
                .resolve(safePathSegment(city))
                .resolve(safePathSegment(prefix))
                .resolve(safePathSegment(segment));
    }

    public static Path getPlainFile(String province, String city, String segment) {
        return getSegmentDir(province, city, segment).resolve(PLAIN_FILE_NAME);
    }

    public static String segmentPrefix(String segment) {
        if (StringUtils.isBlank(segment)) {
            return "";
        }
        String normalized = segment.trim();
        return normalized.length() <= 3 ? normalized : normalized.substring(0, 3);
    }

    public static String safePathSegment(String value) {
        if (StringUtils.isBlank(value)) {
            return "_";
        }
        String trimmed = value.trim();
        return UNSAFE_PATH.matcher(trimmed).replaceAll("_");
    }

    public static String safeFileName(String value) {
        return safePathSegment(value);
    }
}
