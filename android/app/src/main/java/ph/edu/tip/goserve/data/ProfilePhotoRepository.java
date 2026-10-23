package ph.edu.tip.goserve.data;

import org.json.JSONObject;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ProfilePhotoRepository {
    interface Transport {
        String accountId(); void requireCurrent();
        JSONObject rpc(String name,JSONObject args) throws Exception;
        void upload(String path,byte[] jpeg) throws Exception;
        byte[] download(String path) throws Exception;
        void delete(String path) throws Exception;
    }
    public static final class Photo {
        public final String path; public final long version; private final byte[] jpeg;
        public Photo(String path,long version,byte[] jpeg) {this.path=path;this.version=version;this.jpeg=jpeg==null?null:jpeg.clone();}
        public byte[] image() {return jpeg==null?null:jpeg.clone();}
    }
    public static final class Update {
        private final long version;private final byte[] jpeg;
        private final String prepareKey=UUID.randomUUID().toString(),saveKey=UUID.randomUUID().toString();
        private String path;private boolean uploaded;
        public Update(long version,byte[] jpeg) {if(version<0)throw new IllegalArgumentException();this.version=version;this.jpeg=jpeg==null?null:jpeg.clone();}
    }
    public static final class PhotoException extends Exception {
        public final String code;
        public PhotoException(String code) {super(code);this.code=code;}
    }
    private final Transport transport;
    ProfilePhotoRepository(Transport transport) {this.transport=transport;}
    public ProfilePhotoRepository(AccountGateway gateway) {
        this(new Transport(){
            public String accountId(){return gateway.lease().accountId;}
            public void requireCurrent(){gateway.requireCurrent();}
            public JSONObject rpc(String name,JSONObject args)throws Exception{return gateway.rpc(name,args);}
            public void upload(String path,byte[] jpeg)throws Exception{gateway.uploadAvatar(path,jpeg);}
            public byte[] download(String path)throws Exception{return gateway.downloadAvatar(path);}
            public void delete(String path)throws Exception{gateway.deleteRetiredAvatar(path);}
        });
    }
    public void requireCurrent(){transport.requireCurrent();}
    public Photo load()throws Exception{
        requireCurrent();JSONObject value=transport.rpc("profile_avatar",new JSONObject());requireCurrent();return read(value,null,null);
    }
    public synchronized Photo save(Update update)throws Exception{
        requireCurrent();if(update==null)throw invalid();
        if(update.jpeg!=null){
            ProfileImage.validate(update.jpeg);
            if(update.path==null){
                JSONObject ticket=transport.rpc("prepare_profile_avatar",new JSONObject().put("p_expected_version",update.version).put("p_idempotency_key",update.prepareKey));requireCurrent();
                if(ticket.length()!=2 || number(ticket,"expected_version")!=update.version || !isOwnPath(transport.accountId(),ticket.optString("path",null)))throw invalid();
                update.path=ticket.getString("path");
            }
            if(!update.uploaded){
                requireCurrent();
                try{transport.upload(update.path,update.jpeg.clone());}
                catch(SupabaseClient.ApiException e){if(e.status!=409 && !"STORAGE_EXISTS".equals(e.code))throw e;}
                requireCurrent();update.uploaded=true;
            }
        }
        JSONObject result=transport.rpc("set_profile_avatar",new JSONObject().put("p_path",update.path==null?JSONObject.NULL:update.path)
                .put("p_expected_version",update.version).put("p_idempotency_key",update.saveKey));requireCurrent();
        return read(result,update.path,update.jpeg);
    }
    private Photo read(JSONObject value,String savedPath,byte[] savedBytes)throws Exception{
        if(value==null || value.length()!=3 || !value.has("avatar_path"))throw invalid();
        long version=number(value,"avatar_version");Object raw=value.get("avatar_path");
        String path=raw==JSONObject.NULL?null:raw instanceof String?(String)raw:null;
        if(raw!=JSONObject.NULL && !isOwnPath(transport.accountId(),path))throw invalid();
        JSONArray obsolete=value.optJSONArray("obsolete_paths");if(obsolete==null || obsolete.length()>20)throw invalid();
        List<String> cleanup=new ArrayList<>();
        for(int i=0;i<obsolete.length();i++){
            Object name=obsolete.get(i);if(!(name instanceof String) || !isOwnPath(transport.accountId(),(String)name) || name.equals(path) || cleanup.contains(name))throw invalid();cleanup.add((String)name);
        }
        byte[] jpeg=null;
        if(path!=null){requireCurrent();jpeg=path.equals(savedPath)?savedBytes:transport.download(path);requireCurrent();ProfileImage.validate(jpeg);}
        for(String retired:cleanup){
            requireCurrent();try{transport.delete(retired);}catch(Exception failed){requireCurrent();}requireCurrent();
        }
        return new Photo(path,version,jpeg);
    }
    private static long number(JSONObject value,String field)throws Exception{
        Object n=value.opt(field);if(!(n instanceof Integer || n instanceof Long) || ((Number)n).longValue()<0)throw invalid();return ((Number)n).longValue();
    }
    static boolean isOwnPath(String account,String path){return account!=null && path!=null && path.matches(java.util.regex.Pattern.quote(account)+"/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.jpg");}
    private static PhotoException invalid(){return new PhotoException("INVALID_PHOTO");}
}
