package ph.edu.tip.goserve.data;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class ProfilePhotoTest {
    static final String OWNER="00000000-0000-0000-0000-000000000001",OTHER="00000000-0000-0000-0000-000000000002";
    static final String PATH=OWNER+"/00000000-0000-0000-0000-000000000003.jpg",OLD=OWNER+"/00000000-0000-0000-0000-000000000004.jpg";
    @Test public void storageDuplicateResponsesHaveAnExplicitSafeRetryCode(){
        assertEquals("STORAGE_EXISTS",SupabaseClient.apiError(400,"{\"statusCode\":\"409\",\"error\":\"Duplicate\",\"message\":\"private details\"}").code);
        assertEquals("UNKNOWN",SupabaseClient.apiError(400,"{\"error\":\"Other\",\"message\":\"private details\"}").code);
    }
    @Test public void pickedImageIsBoundedAspectPreservingJpegAndStripsTransparency() throws Exception {
        byte[] source=image(1600,800,Bitmap.CompressFormat.PNG);byte[] jpeg=ProfileImage.normalize(new ByteArrayInputStream(source));
        Bitmap b=BitmapFactory.decodeByteArray(jpeg,0,jpeg.length);assertEquals(512,b.getWidth());assertEquals(256,b.getHeight());assertFalse(b.hasAlpha());assertTrue(jpeg.length<=ProfileImage.MAX_BYTES);assertEquals(255,jpeg[0]&255);assertEquals(216,jpeg[1]&255);b.recycle();
    }
    @Test public void corruptOversizedAndUnsupportedSourcesAreRejected() throws Exception {
        for(byte[] bytes:new byte[][]{new byte[0],new byte[]{1,2,3},new byte[ProfileImage.MAX_SOURCE_BYTES+1]})try{ProfileImage.normalize(new ByteArrayInputStream(bytes));fail();}catch(IOException expected){}
    }
    @Test public void extremelyWideImagesAreRejectedBeforeDecodeEvenWithFewPixels()throws Exception{
        byte[] source=image(20001,32,Bitmap.CompressFormat.PNG);
        try{ProfileImage.normalize(new ByteArrayInputStream(source));fail("Each source dimension needs a decode bound, not only its pixel count");}catch(IOException expected){}
    }
    @Test public void portraitExifIsNormalizedAndTheOrientationTagIsRemoved()throws Exception{
        byte[] jpeg=image(80,40,Bitmap.CompressFormat.JPEG);
        byte[] exif={ (byte)255,(byte)225,0,34,69,120,105,102,0,0,73,73,42,0,8,0,0,0,1,0,18,1,3,0,1,0,0,0,6,0,0,0,0,0,0,0 };
        ByteArrayOutputStream source=new ByteArrayOutputStream();source.write(jpeg,0,2);source.write(exif);source.write(jpeg,2,jpeg.length-2);
        byte[] result=ProfileImage.normalize(new ByteArrayInputStream(source.toByteArray()));Bitmap b=BitmapFactory.decodeByteArray(result,0,result.length);assertEquals(40,b.getWidth());assertEquals(80,b.getHeight());b.recycle();
        android.media.ExifInterface metadata=new android.media.ExifInterface(new ByteArrayInputStream(result));assertEquals(android.media.ExifInterface.ORIENTATION_UNDEFINED,metadata.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,0));
    }
    @Test public void readUsesPrivateCanonicalMetadataAndCleansOnlyRetiredOwnerPaths() throws Exception {
        Wire w=new Wire();w.photo=state(PATH,4).put("obsolete_paths",new JSONArray().put(OLD));ProfilePhotoRepository.Photo p=new ProfilePhotoRepository(w).load();
        assertEquals(PATH,p.path);assertEquals(4,p.version);assertArrayEquals(w.jpeg,p.image());assertEquals(List.of(PATH),w.downloads);assertEquals(List.of(OLD),w.deletes);assertEquals("profile_avatar",w.names.get(0));
    }
    @Test public void savePreparesUploadsAndCommitsWithSeparateRetainedKeys() throws Exception {
        Wire w=new Wire();ProfilePhotoRepository repo=new ProfilePhotoRepository(w);ProfilePhotoRepository.Update op=new ProfilePhotoRepository.Update(0,w.jpeg);
        ProfilePhotoRepository.Photo p=repo.save(op);assertEquals(PATH,p.path);assertEquals(1,p.version);assertEquals(List.of("prepare_profile_avatar","set_profile_avatar"),w.names);assertEquals(List.of(PATH),w.uploads);
        assertEquals(0,w.args.get(0).getLong("p_expected_version"));assertEquals(PATH,w.args.get(1).getString("p_path"));assertNotEquals(w.args.get(0).getString("p_idempotency_key"),w.args.get(1).getString("p_idempotency_key"));
    }
    @Test public void uncertainCommitRetryReusesThePathBytesAndKeyWithoutAnotherUpload() throws Exception {
        Wire w=new Wire();ProfilePhotoRepository repo=new ProfilePhotoRepository(w);ProfilePhotoRepository.Update op=new ProfilePhotoRepository.Update(0,w.jpeg);w.failCommit=true;
        try{repo.save(op);fail();}catch(IOException expected){}String first=w.args.get(1).getString("p_idempotency_key");w.failCommit=false;repo.save(op);
        assertEquals(1,w.uploads.size());assertEquals(first,w.args.get(2).getString("p_idempotency_key"));
    }
    @Test public void uncertainUploadCanRecoverFromConflictThroughTheVersionedCommit() throws Exception {
        Wire w=new Wire();ProfilePhotoRepository repo=new ProfilePhotoRepository(w);ProfilePhotoRepository.Update op=new ProfilePhotoRepository.Update(0,w.jpeg);w.failUpload=true;
        try{repo.save(op);fail();}catch(IOException expected){}w.failUpload=false;w.uploadConflict=true;assertEquals(PATH,repo.save(op).path);assertEquals(2,w.uploads.size());
    }
    @Test public void removalUsesNoUploadAndKeepsTheExpectedVersion() throws Exception {
        Wire w=new Wire();ProfilePhotoRepository.Photo p=new ProfilePhotoRepository(w).save(new ProfilePhotoRepository.Update(7,null));assertNull(p.path);assertEquals(8,p.version);assertEquals(List.of("set_profile_avatar"),w.names);assertTrue(w.args.get(0).isNull("p_path"));assertTrue(w.uploads.isEmpty());
    }
    @Test public void wrongOwnerMalformedVersionsAndUnsafeCleanupNeverReachStorage() throws Exception {
        for(JSONObject response:new JSONObject[]{state(PATH.replace(OWNER,OTHER),0),state(null,-1),state(null,0).put("avatar_version",1.5),state(null,0).put("obsolete_paths",new JSONArray().put(PATH.replace(OWNER,OTHER))),state(PATH,0).put("obsolete_paths",new JSONArray().put(PATH))}){
            Wire w=new Wire();w.photo=response;try{new ProfilePhotoRepository(w).load();fail();}catch(ProfilePhotoRepository.PhotoException expected){assertEquals("INVALID_PHOTO",expected.code);}assertTrue(w.downloads.isEmpty());assertTrue(w.deletes.isEmpty());
        }
    }
    @Test public void accountReplacementAfterRpcOrUploadPreventsAnyFurtherAction() throws Exception {
        Wire w=new Wire();w.loseAfterRpc=true;try{new ProfilePhotoRepository(w).load();fail();}catch(IllegalStateException expected){}assertTrue(w.downloads.isEmpty());
        w=new Wire();w.loseAfterUpload=true;try{new ProfilePhotoRepository(w).save(new ProfilePhotoRepository.Update(0,w.jpeg));fail();}catch(IllegalStateException expected){}assertEquals(List.of("prepare_profile_avatar"),w.names);
    }
    @Test public void cleanupFailureDoesNotUndoASavedPhotoButAccountLossIsNeverSwallowed() throws Exception {
        Wire w=new Wire();w.photo=state(PATH,1).put("obsolete_paths",new JSONArray().put(OLD));w.failDelete=true;assertEquals(PATH,new ProfilePhotoRepository(w).load().path);
        w=new Wire();w.photo=state(PATH,1).put("obsolete_paths",new JSONArray().put(OLD));w.loseAfterDelete=true;try{new ProfilePhotoRepository(w).load();fail();}catch(IllegalStateException expected){}
    }
    @Test public void updatesDefensivelyCopyBytesAndRejectUnnormalizedContent() throws Exception {
        Wire w=new Wire();byte[] copy=w.jpeg.clone();ProfilePhotoRepository.Update op=new ProfilePhotoRepository.Update(0,copy);copy[0]=0;new ProfilePhotoRepository(w).save(op);assertEquals(255,w.lastUpload[0]&255);
        for(byte[] bad:new byte[][]{new byte[]{1,2,3},image(1024,1024,Bitmap.CompressFormat.JPEG),image(40,40,Bitmap.CompressFormat.PNG)})try{new ProfilePhotoRepository(w).save(new ProfilePhotoRepository.Update(0,bad));fail();}catch(IOException expected){}
    }
    static byte[] image(int width,int height,Bitmap.CompressFormat format){Bitmap b=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);b.eraseColor(android.graphics.Color.rgb(80,100,120));ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(format,88,out);b.recycle();return out.toByteArray();}
    static JSONObject state(String path,long version)throws Exception{return new JSONObject().put("avatar_path",path==null?JSONObject.NULL:path).put("avatar_version",version).put("obsolete_paths",new JSONArray());}
    static final class Wire implements ProfilePhotoRepository.Transport{
        boolean current=true,failCommit,failUpload,uploadConflict,failDelete,loseAfterRpc,loseAfterUpload,loseAfterDelete;
        JSONObject photo;byte[] jpeg=image(32,32,Bitmap.CompressFormat.JPEG),lastUpload;
        List<String> names=new ArrayList<>(),uploads=new ArrayList<>(),downloads=new ArrayList<>(),deletes=new ArrayList<>();List<JSONObject> args=new ArrayList<>();
        public String accountId(){return OWNER;}public void requireCurrent(){if(!current)throw new IllegalStateException("STALE_ACCOUNT");}
        public JSONObject rpc(String name,JSONObject a)throws Exception{names.add(name);args.add(a);if(loseAfterRpc)current=false;if(name.equals("profile_avatar"))return photo==null?state(null,0):photo;if(name.equals("prepare_profile_avatar"))return new JSONObject().put("path",PATH).put("expected_version",a.getLong("p_expected_version"));if(failCommit)throw new IOException("private response");return state(a.isNull("p_path")?null:a.getString("p_path"),a.getLong("p_expected_version")+1);}
        public void upload(String path,byte[] bytes)throws Exception{uploads.add(path);lastUpload=bytes;if(loseAfterUpload)current=false;if(failUpload)throw new IOException("unknown upload");if(uploadConflict)throw new SupabaseClient.ApiException(409,"CONFLICT","already exists");}
        public byte[] download(String path){downloads.add(path);return jpeg;}
        public void delete(String path)throws Exception{deletes.add(path);if(loseAfterDelete){current=false;throw new IOException();}if(failDelete)throw new IOException();}
    }
}
