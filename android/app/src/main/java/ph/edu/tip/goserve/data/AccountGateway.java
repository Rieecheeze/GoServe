package ph.edu.tip.goserve.data;

import org.json.JSONArray;
import org.json.JSONObject;

/** A snapshot of one authenticated account; obtain another after account replacement. */
public final class AccountGateway {
    private final SupabaseClient client;
    private final AuthRepository auth;
    private final AuthRepository.SessionLease lease;
    public AccountGateway(SupabaseClient client, AuthRepository auth) {
        this.client = client; this.auth = auth; this.lease = auth.sessionLease();
    }
    public AuthRepository.SessionLease lease() { return lease; }
    public void requireCurrent() { auth.requireCurrent(lease); }
    public JSONArray get(String relativePath) throws Exception {
        requireCurrent();
        java.net.URI path=java.net.URI.create(relativePath);
        if (path.isAbsolute() || path.getAuthority()!=null || path.getFragment()!=null || relativePath.contains("\\")
                || !path.getPath().matches("/rest/v1/[a-z][a-z0-9_]*")) throw new IllegalArgumentException("Expected an authorized table read.");
        android.net.Uri query=android.net.Uri.parse(relativePath);
        String limit=query.getQueryParameter("limit"), columns=query.getQueryParameter("select");
        if (limit==null || !limit.matches("[0-9]{1,3}") || Integer.parseInt(limit)<1 || Integer.parseInt(limit)>100
                || columns==null || columns.contains("*")) throw new IllegalArgumentException("Use explicit columns and a bounded page size.");
        JSONArray value=client.array(relativePath,auth.token(lease)); requireCurrent(); return value;
    }
    public JSONObject rpc(String name, JSONObject args) throws Exception {
        requireCurrent(); String path=rpcPath(name);
        JSONObject value=client.object("POST",path,args,auth.token(lease)); requireCurrent(); return value;
    }
    public Object rpcValue(String name, JSONObject args) throws Exception {
        requireCurrent(); Object value=client.value("POST",rpcPath(name),args,auth.token(lease)); requireCurrent(); return value;
    }
    public JSONObject edge(String name, JSONObject args) throws Exception {
        requireCurrent(); JSONObject value=client.object("POST",edgePath(name),args,auth.token(lease)); requireCurrent(); return value;
    }
    public JSONObject upload(String edgeName, String fileId, byte[] bytes, String mimeType) throws Exception {
        requireCurrent(); JSONObject value=client.upload(edgePath(edgeName),fileId,bytes,mimeType,auth.token(lease)); requireCurrent(); return value;
    }
    public long downloadTo(String edgeName, JSONObject args, java.io.OutputStream destination,
                           long maximumBytes, String expectedSha256) throws Exception {
        requireCurrent(); long count=client.downloadTo(edgePath(edgeName),args,auth.token(lease),destination,maximumBytes,expectedSha256,this::requireCurrent);
        requireCurrent(); return count;
    }
    public void uploadAvatar(String path,byte[] jpeg)throws Exception {
        requireAvatar(path); ProfileImage.validate(jpeg);
        client.upload("/storage/v1/object/profile-avatars/"+path,path.substring(path.indexOf('/')+1,path.length()-4),jpeg,"image/jpeg",auth.token(lease));requireCurrent();
    }
    public byte[] downloadAvatar(String path)throws Exception {
        requireAvatar(path);java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
        client.downloadTo("/storage/v1/object/authenticated/profile-avatars/"+path,null,auth.token(lease),bytes,ProfileImage.MAX_BYTES,null,this::requireCurrent);
        requireCurrent();return bytes.toByteArray();
    }
    public void deleteRetiredAvatar(String path)throws Exception {
        requireAvatar(path);client.value("DELETE","/storage/v1/object/profile-avatars",new JSONObject().put("prefixes",new JSONArray().put(path)),auth.token(lease));requireCurrent();
    }
    private void requireAvatar(String path){requireCurrent();if(!ProfilePhotoRepository.isOwnPath(lease.accountId,path))throw new IllegalArgumentException("Expected an owned profile photo.");}
    private String rpcPath(String name) {
        if (name==null || !name.matches("[a-z][a-z0-9_]*") || java.util.Arrays.asList("registration_operation","file_server_operation","download_server_operation","cleanup_claim","cleanup_finish").contains(name))
            throw new IllegalArgumentException("Expected a public GoServe operation.");
        return "/rest/v1/rpc/"+name;
    }
    private String edgePath(String name) {
        if (name==null || !name.matches("[a-z][a-z0-9-]*") || java.util.Arrays.asList("cleanup-files","egovpay-callback","register-student","recovery-request").contains(name))
            throw new IllegalArgumentException("Expected an authenticated GoServe operation.");
        return "/functions/v1/"+name;
    }
}
