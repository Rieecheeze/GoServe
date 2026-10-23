package ph.edu.tip.goserve.data;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.io.OutputStream;
import java.security.MessageDigest;

public final class SupabaseClient {
    private final String baseUrl;
    private final String publishableKey;

    public SupabaseClient(String baseUrl, String publishableKey) {
        URI uri = URI.create(baseUrl);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null || uri.getPort() != -1 && uri.getPort() != 443
                || uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath())) {
            throw new IllegalArgumentException("Supabase URL must use HTTPS.");
        }
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.publishableKey = publishableKey;
    }

    public String baseUrl() { return baseUrl; }

    public JSONObject object(String method, String path, JSONObject body, String token) throws Exception {
        String response = request(method, path, body, token);
        return response.isEmpty() ? new JSONObject() : (JSONObject)SupabaseJson.parse(response);
    }

    public JSONArray array(String path, String token) throws Exception {
        return (JSONArray)SupabaseJson.parse(request("GET", path, null, token));
    }

    public Object value(String method, String path, JSONObject body, String token) throws Exception {
        String response = request(method,path,body,token);
        return response.isEmpty() ? JSONObject.NULL : SupabaseJson.parse(response);
    }

    public JSONObject upload(String path, String fileId, byte[] bytes, String mimeType, String token) throws Exception {
        if (fileId == null || !fileId.matches("[A-Fa-f0-9-]{36}") || mimeType == null || mimeType.contains("\r") || mimeType.contains("\n"))
            throw new IllegalArgumentException("Invalid file upload details.");
        HttpURLConnection connection = connect("POST",path,token);
        try {
            connection.setDoOutput(true); connection.setRequestProperty("Content-Type",mimeType);
            connection.setRequestProperty("X-GoServe-Upload",fileId); connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out=connection.getOutputStream()) { out.write(bytes); }
            int status=connection.getResponseCode(); String response=read(status<400?connection.getInputStream():connection.getErrorStream());
            if (status<200 || status>=300) throw apiError(status,response);
            return (JSONObject)SupabaseJson.parse(response);
        } finally { connection.disconnect(); }
    }

    public long downloadTo(String path, JSONObject body, String token, OutputStream destination,
                           long maximumBytes, String expectedSha256, Runnable checkCurrent) throws Exception {
        if (maximumBytes<=0 || expectedSha256!=null && !expectedSha256.matches("[A-Fa-f0-9]{64}")) throw new IllegalArgumentException("Invalid file download bounds.");
        HttpURLConnection connection=connect(body==null?"GET":"POST",path,token);
        try {
            if(body!=null){
                byte[] json=body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true); connection.setRequestProperty("Content-Type","application/json"); connection.setFixedLengthStreamingMode(json.length);
                try (OutputStream out=connection.getOutputStream()) { out.write(json); }
            }
            int status=connection.getResponseCode();
            if (status<200 || status>=300) throw apiError(status,read(connection.getErrorStream()));
            long declared=connection.getContentLengthLong();
            if (declared>maximumBytes) throw new IOException("The file exceeds the download limit.");
            MessageDigest digest=MessageDigest.getInstance("SHA-256"); long total=0;
            try (InputStream input=connection.getInputStream()) {
                byte[] buffer=new byte[16*1024]; int count;
                while ((count=input.read(buffer))!=-1) {
                    checkCurrent.run();
                    if (count>maximumBytes-total) throw new IOException("The file exceeds the download limit.");
                    destination.write(buffer,0,count); digest.update(buffer,0,count); total+=count;
                }
            }
            checkCurrent.run();
            if (declared>=0 && declared!=total) throw new IOException("The file download is incomplete.");
            byte[] actual=digest.digest();
            if (expectedSha256!=null) {
                byte[] expected=new byte[32]; for (int i=0;i<32;i++) expected[i]=(byte)Integer.parseInt(expectedSha256.substring(i*2,i*2+2),16);
                if (!MessageDigest.isEqual(expected,actual)) throw new IOException("The file could not be verified. Please retry.");
            }
            destination.flush(); return total;
        } finally { connection.disconnect(); }
    }

    private HttpURLConnection connect(String method,String path,String token) throws Exception {
        validatePath(path);
        HttpURLConnection connection=(HttpURLConnection)URI.create(baseUrl+path).toURL().openConnection();
        connection.setRequestMethod(method); connection.setConnectTimeout(15000); connection.setReadTimeout(25000);
        connection.setInstanceFollowRedirects(false); connection.setRequestProperty("apikey",publishableKey); connection.setRequestProperty("Accept","application/json");
        if (token!=null && !token.isEmpty()) connection.setRequestProperty("Authorization","Bearer "+token);
        return connection;
    }
    private void validatePath(String path) {
        if (path==null || !path.startsWith("/") || path.startsWith("//") || path.contains("\\") || path.contains("#")) throw new IllegalArgumentException("Expected a Supabase relative path.");
        URI relative=URI.create(path);
        if (relative.isAbsolute() || relative.getAuthority()!=null || relative.getPath().contains("..")) throw new IllegalArgumentException("Expected a Supabase relative path.");
    }

    private String request(String method, String path, JSONObject body, String token) throws Exception {
        HttpURLConnection connection=connect(method,path,token);
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
                throw apiError(status,response);
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
        public final String code;
        public ApiException(int status, String message) { this(status,"UNKNOWN",message); }
        public ApiException(int status,String code,String message) { super(message); this.status=status; this.code=code; }
    }
    static ApiException apiError(int status,String response) {
        String code="UNKNOWN";
        try {
            JSONObject error=new JSONObject(response);
            if (error.opt("error") instanceof JSONObject) code=error.getJSONObject("error").optString("code","UNKNOWN");
            else {
                String sql=error.optString("code","");
                if ("P0001".equals(sql)) code=error.optString("message","UNKNOWN");
                else if (java.util.Arrays.asList("22P02","23502","23514").contains(sql)) code="VALIDATION_FAILED";
                else if ("23505".equals(sql)) code="CONFLICT";
                else if ("40P01".equals(sql) || "40001".equals(sql)) code="TEMPORARILY_UNAVAILABLE";
                else if ((status==400 || status==409) && ("Duplicate".equals(error.optString("error")) || "ResourceAlreadyExists".equals(sql) || "KeyAlreadyExists".equals(sql))) code="STORAGE_EXISTS";
            }
        } catch (Exception ignored) {}
        java.util.Set<String> known=new java.util.HashSet<>(java.util.Arrays.asList("AUTH_REQUIRED","AUTH_INVALID","FORBIDDEN","NOT_FOUND","VALIDATION_FAILED","PROFILE_INCOMPLETE","TERMS_REQUIRED","CONFIGURATION_REQUIRED","STALE_VERSION","QUOTE_STALE","IDEMPOTENCY_CONFLICT","INVALID_STATE","PAYMENT_REQUIRED","ALREADY_PAID","CODE_INVALID","CODE_EXPIRED","CODE_CONSUMED","CAPACITY_FULL","POLICY_BLOCKED","UPLOAD_INVALID","DOWNLOAD_UNCONFIRMED","CONSENT_REQUIRED","PROVIDER_UNAVAILABLE","CLEANUP_REQUIRED","TEMPORARILY_UNAVAILABLE","IMMUTABLE_RECORD","CONFLICT"));
        known.add("STORAGE_EXISTS");
        known.add("CHECKOUT_UNCONFIRMED");
        if (!known.contains(code)) code="UNKNOWN";
        String message="STALE_VERSION".equals(code) || "QUOTE_STALE".equals(code) ? "This information changed. Review the latest version and retry."
                : "TERMS_REQUIRED".equals(code) ? "Review and accept the approved Terms to continue."
                : "CONFIGURATION_REQUIRED".equals(code) ? "This GoServe feature needs server setup. Please retry later."
                : "PROVIDER_UNAVAILABLE".equals(code) ? "This service is not available yet."
                : status==401 ? "Your session expired. Sign in again." : status==403 ? "This account is not authorized for this action."
                : status==429 ? "Too many attempts. Wait a moment and retry." : status>=500 ? "GoServe could not reach the service. Please retry."
                : status==404 ? "This GoServe feature has not been configured on the server yet." : "The request was not accepted. Check the details and retry.";
        return new ApiException(status,code,message);
    }
}
