package cn.cordys.crm.integration.common.utils;

import org.apache.commons.lang3.StringUtils;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.Map;
import java.util.Objects;

public class HttpRequestUtil {

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final SSLSocketFactory insecureSslSocketFactory = buildInsecureSslSocketFactory();

    // 发送 GET 请求
    public static String sendGetRequest(String url, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET();

        if (headers != null) {
            headers.forEach(requestBuilder::header);
        }

        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return handleResponse(response.statusCode(), response.body());
    }

    // 发送 POST 请求
    public static String sendPostRequest(String url, String body, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .header("Content-Type", "application/json");

        if (headers != null) {
            headers.forEach(requestBuilder::header);
        }

        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return handleResponse(response.statusCode(), response.body());
    }

    /**
     * MMBA 测试环境使用 IP + 非标准证书，显式关闭主机名校验。
     * 这里只用于 MMBA 的专用出站，避免影响其它通用 HTTP 能力。
     */
    public static String postString(String url, String companyCode, String body) throws IOException, InterruptedException {
        HttpURLConnection connection = openMmbaConnection(url, "POST", companyCode);
        try {
            writeRequestBody(connection, body);
            int statusCode = connection.getResponseCode();
            String responseBody = readResponseBody(connection, statusCode);
            return handleResponse(statusCode, responseBody);
        } finally {
            connection.disconnect();
        }
    }

    public static BinaryResponse postBinary(String url, String companyCode, String body) throws IOException, InterruptedException {
        HttpURLConnection connection = openMmbaConnection(url, "POST", companyCode);
        try {
            writeRequestBody(connection, body);
            return buildBinaryResponse(connection);
        } finally {
            connection.disconnect();
        }
    }

    public static BinaryResponse getBinary(String url) throws IOException, InterruptedException {
        HttpURLConnection connection = openMmbaConnection(url, "GET", null);
        try {
            return buildBinaryResponse(connection);
        } finally {
            connection.disconnect();
        }
    }

    private static String handleResponse(int statusCode, String responseBody) {
        if (statusCode >= 200 && statusCode < 300) {
            return responseBody;
        }
        return "Error: " + statusCode + " - " + responseBody;
    }

    /**
     * URL 转换
     */
    public static String urlTransfer(String urlPattern, Object... params) {
        Object[] vars = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            if (Objects.isNull(params[i])) {
                vars[i] = "";
                continue;
            }
            String var = StringUtils.stripToEmpty(params[i].toString());
            vars[i] = URLEncoder.encode(var, StandardCharsets.UTF_8);
        }
        return MessageFormat.format(urlPattern, vars);
    }

    private static SSLSocketFactory buildInsecureSslSocketFactory() {
        try {
            TrustManager[] trustManagers = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagers, new SecureRandom());
            return sslContext.getSocketFactory();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("构造忽略 SSL 校验的连接工厂失败", e);
        }
    }

    private static HttpURLConnection openMmbaConnection(String url, String method, String companyCode) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        if (connection instanceof HttpsURLConnection httpsConnection) {
            httpsConnection.setSSLSocketFactory(insecureSslSocketFactory);
            httpsConnection.setHostnameVerifier((hostname, session) -> true);
        }
        connection.setRequestMethod(method);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(20000);
        connection.setDoInput(true);
        connection.setUseCaches(false);
        connection.setRequestProperty("Content-Type", "application/json");
        if (StringUtils.isNotBlank(companyCode)) {
            connection.setRequestProperty("Api-Info", companyCode);
        }
        if ("POST".equalsIgnoreCase(method)) {
            connection.setDoOutput(true);
        }
        return connection;
    }

    private static void writeRequestBody(HttpURLConnection connection, String body) throws IOException {
        byte[] requestBody = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(requestBody);
        }
    }

    private static BinaryResponse buildBinaryResponse(HttpURLConnection connection) throws IOException {
        int statusCode = connection.getResponseCode();
        byte[] responseBody = readResponseBytes(connection, statusCode);
        return new BinaryResponse(statusCode, responseBody, connection.getHeaderField("Location"));
    }

    private static String readResponseBody(HttpURLConnection connection, int statusCode) throws IOException {
        return new String(readResponseBytes(connection, statusCode), StandardCharsets.UTF_8);
    }

    private static byte[] readResponseBytes(HttpURLConnection connection, int statusCode) throws IOException {
        InputStream inputStream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (inputStream == null) {
            return new byte[0];
        }
        try (InputStream stream = inputStream) {
            return stream.readAllBytes();
        }
    }

    public record BinaryResponse(int statusCode, byte[] body, String location) {
    }
}
