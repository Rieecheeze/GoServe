package ph.edu.tip.goserve.data;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public final class SupabaseClient {
    private final String baseUrl;
    private final String publishableKey;

    public SupabaseClient(String baseUrl, String publishableKey) {
        URI uri = URI.create(baseUrl);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalArgumentException("Supabase URL must use HTTPS.");
        }
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.publishableKey = publishableKey;
    }

    public String baseUrl() { return baseUrl; }

    public JSONObject object(String method, String path, JSONObject body, String token) throws Exception {
        String response = request(method, path, body, token);
        return response.isEmpty() ? new JSONObject() : new JSONObject(response);
    }

    public JSONArray array(String path, String token) throws Exception {
        return new JSONArray(request("GET", path, null, token));
    }

    private String request(String method, String path, JSONObject body, String token) throws Exception {
        if (!path.startsWith("/") || path.startsWith("//")) {
            throw new IllegalArgumentException("Expected a Supabase relative path.");
        }
        HttpURLConnection connection = (HttpURLConnection) URI.create(baseUrl + path).toURL().openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(25000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("apikey", publishableKey);
        connection.setRequestProperty("Accept", "application/json");
        if (token != null && !token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
        try {
            if (body != null) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (java.io.OutputStream out = connection.getOutputStream()) { out.write(bytes); }
            }
            int status = connection.getResponseCode();
            String response = read(status < 400 ? connection.getInputStream() : connection.getErrorStream());
            if (status < 200 || status >= 300) {
                throw new ApiException(status, status == 401 ? "Your session expired. Sign in again."
                        : status == 403 ? "This account is not authorized for this action."
                        : status == 429 ? "Too many attempts. Wait a moment and retry."
                        : status >= 500 ? "GoServe could not reach the service. Please retry."
                        : status == 404 ? "This GoServe feature has not been configured on the server yet."
                        : "The request was not accepted. Check the details and retry.");
            }
            return response;
        } finally { connection.disconnect(); }
    }

    private String read(InputStream input) throws IOException {
        if (input == null) return "";
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                if (output.size() + read > 2 * 1024 * 1024) throw new IOException("Service response is too large.");
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    public static final class ApiException extends IOException {
        public final int status;
        public ApiException(int status, String message) { super(message); this.status = status; }
    }
}
