package cn.cordys.tenant.util;

import org.apache.commons.lang3.StringUtils;

/**
 * MySQL JDBC URL 辅助：替换库名、生成无库名的连接串（用于建库）。
 */
public final class JdbcUrlUtils {

    private JdbcUrlUtils() {
    }

    /**
     * 将 jdbc:mysql://host:port/oldDb?params 中的库名替换为 newDbName。
     */
    public static String replaceMysqlDatabase(String jdbcUrl, String newDbName) {
        if (StringUtils.isBlank(jdbcUrl) || !jdbcUrl.startsWith("jdbc:mysql://")) {
            return jdbcUrl;
        }
        String rest = jdbcUrl.substring("jdbc:mysql://".length());
        int slash = rest.indexOf('/');
        if (slash < 0) {
            return jdbcUrl + "/" + newDbName;
        }
        String hostPort = rest.substring(0, slash);
        String afterSlash = rest.substring(slash + 1);
        int q = afterSlash.indexOf('?');
        if (q >= 0) {
            return "jdbc:mysql://" + hostPort + "/" + newDbName + afterSlash.substring(q);
        }
        return "jdbc:mysql://" + hostPort + "/" + newDbName;
    }

    /**
     * 去掉库名，仅保留 jdbc:mysql://host:port/?params，用于执行 CREATE DATABASE。
     */
    public static String mysqlUrlWithoutDatabase(String jdbcUrl) {
        if (StringUtils.isBlank(jdbcUrl) || !jdbcUrl.startsWith("jdbc:mysql://")) {
            return jdbcUrl;
        }
        String rest = jdbcUrl.substring("jdbc:mysql://".length());
        int slash = rest.indexOf('/');
        if (slash < 0) {
            return jdbcUrl.endsWith("/") ? jdbcUrl : jdbcUrl + "/";
        }
        String hostPort = rest.substring(0, slash);
        String afterSlash = rest.substring(slash + 1);
        int q = afterSlash.indexOf('?');
        if (q >= 0) {
            return "jdbc:mysql://" + hostPort + "/" + afterSlash.substring(q);
        }
        return "jdbc:mysql://" + hostPort + "/";
    }
}
