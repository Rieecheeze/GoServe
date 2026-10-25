package ph.edu.tip.goserve.data;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import org.json.JSONObject;
import ph.edu.tip.goserve.domain.AccountValidation;
import ph.edu.tip.goserve.domain.RecoveryData;
import ph.edu.tip.goserve.domain.SessionProfile;

public final class RecoveryRepository implements AutoCloseable {
    public interface Transport {
        JSONObject control(JSONObject input)throws Exception;
        JSONObject recover(JSONObject input)throws Exception;
        void requireCurrent();
        SessionProfile signInPassword(String email,String password)throws Exception;
        default void discardSignIn(SessionProfile profile){}
    }
    private final Transport transport;
    private volatile boolean closed;
    private volatile int generation;
    private boolean finished,signingIn;
    private String offeredGeneration,email,grant,verifier,operationId,resultState;
    private long grantExpiresAt,claimedAt;
    private byte[] passwordDigest;
    public RecoveryRepository(AuthRepository auth){this(new Transport(){
        private volatile long expected=auth.generation();
        private volatile AuthRepository.SessionLease lease=auth.profile()==null?null:auth.sessionLease();
        public void requireCurrent(){if(expected!=auth.generation())throw changed();if(lease!=null)auth.requireCurrent(lease);}
        public JSONObject control(JSONObject input)throws Exception{requireCurrent();if(lease==null)throw new IllegalStateException("Sign in to manage recovery codes.");JSONObject result=auth.recoveryClient().object("POST","/functions/v1/recovery-control",input,auth.token(lease));requireCurrent();return result;}
        public JSONObject recover(JSONObject input)throws Exception{requireCurrent();JSONObject result=auth.recoveryClient().object("POST","/functions/v1/account-recovery",input,null);requireCurrent();return result;}
        public SessionProfile signInPassword(String email,String password)throws Exception{
            SessionProfile value=auth.signInAfterRecovery(email,password,expected);
            synchronized(auth){if(value!=auth.profile())throw changed();expected=auth.generation();lease=auth.sessionLease();}
            return value;
        }
        public void discardSignIn(SessionProfile profile){auth.discardRecoverySignIn(profile);}
    });}
    public RecoveryRepository(Transport transport){if(transport==null)throw new IllegalArgumentException("A recovery transport is required.");this.transport=transport;}
    public void requireCurrent(){if(closed)throw changed();transport.requireCurrent();}
    private int request(){requireCurrent();synchronized(this){if(finished)throw new IllegalStateException("This recovery flow has finished. Start a new flow.");return generation;}}
    private void current(int request){requireCurrent();if(request!=generation)throw changed();}
    private JSONObject control(JSONObject input,int request)throws Exception{current(request);JSONObject result=transport.control(input);current(request);return result;}
    public RecoveryData.Status status()throws Exception{int request=request();RecoveryData.Status result=RecoveryData.status(control(new JSONObject().put("action","status"),request));current(request);return result;}
    public RecoveryData.Codes generateCodes()throws Exception{
        int request=request();synchronized(this){offeredGeneration=null;}
        RecoveryData.Codes result=RecoveryData.codes(control(new JSONObject().put("action","generate_codes"),request));
        synchronized(this){current(request);offeredGeneration=result.generation;}return result;
    }
    public RecoveryData.Status acknowledgeCodes(String generation)throws Exception{
        int request=request();RecoveryData.uuid(generation);synchronized(this){if(!generation.equals(offeredGeneration))throw new IllegalStateException("Generate and save this recovery-code set before acknowledging it.");}
        RecoveryData.Status result=RecoveryData.status(control(new JSONObject().put("action","acknowledge_codes").put("generation",generation),request));
        if(!generation.equals(result.generation) || !result.codesAcknowledged || result.requiresSetup)throw new IllegalArgumentException("Saving recovery codes could not be confirmed. Refresh status.");
        synchronized(this){current(request);offeredGeneration=null;}return result;
    }
    public void verifyProof(String email,String code)throws Exception{
        int request=request();String normalized=email==null?"":email.trim();String proof=code==null?"":code.replaceAll("[\\s-]","").toUpperCase(java.util.Locale.ROOT);
        if(!AccountValidation.isValidEmail(normalized) || normalized.length()>254 || !proof.matches("[A-Z2-7]{32}"))throw new IllegalArgumentException("Enter your account email and a saved recovery code.");
        synchronized(this){if(grant!=null || operationId!=null)throw new IllegalStateException("Finish the current recovery attempt before requesting another proof.");}
        byte[] random=new byte[32];new SecureRandom().nextBytes(random);String candidate=Base64.getUrlEncoder().withoutPadding().encodeToString(random);Arrays.fill(random,(byte)0);
        String challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(digest(candidate));
        JSONObject input=new JSONObject().put("action","verify").put("email",normalized).put("code",proof).put("code_challenge",challenge).put("code_challenge_method","s256");
        JSONObject value=transport.recover(input);current(request);RecoveryData.exact(value,"reset_grant","expires_at");
        String token=RecoveryData.text(value,"reset_grant");if(!token.matches("[A-Za-z0-9_-]{43}"))throw new IllegalArgumentException("Recovery verification could not be confirmed. Start again.");
        long expires=Instant.parse(RecoveryData.text(value,"expires_at")).toEpochMilli(),now=System.currentTimeMillis();
        if(expires<=now || expires>now+360000)throw new IllegalArgumentException("Recovery verification expired. Verify a saved code again.");
        synchronized(this){current(request);this.email=normalized;grant=token;verifier=candidate;grantExpiresAt=expires;}
    }
    public RecoveryData.ResetResult resetPassword(String password)throws Exception{
        int request=request();validatePassword(password);String id,token,binding;
        synchronized(this){
            current(request);if(operationId!=null)throw new IllegalStateException("Check the pending password result instead of submitting it again.");
            if(grant==null || grantExpiresAt<=System.currentTimeMillis())throw new IllegalStateException("Verify a saved recovery code before setting a password.");
            operationId=UUID.randomUUID().toString();id=operationId;token=grant;binding=verifier;claimedAt=System.currentTimeMillis();passwordDigest=digest(password);resultState="pending";
        }
        try{
            JSONObject value=transport.recover(new JSONObject().put("action","reset").put("reset_grant",token).put("code_verifier",binding).put("password",password).put("operation_id",id));
            current(request);return acceptResult(value,id,request);
        }catch(SupabaseClient.ApiException failure){
            current(request);if(failure.status>=500 || "RECOVERY_OPERATION_PENDING".equals(failure.code))return new RecoveryData.ResetResult("pending",id);
            synchronized(this){clearReset();}return new RecoveryData.ResetResult("failed",id);
        }catch(java.io.IOException | org.json.JSONException | IllegalArgumentException uncertain){current(request);return new RecoveryData.ResetResult("pending",id);}
    }
    public RecoveryData.ResetResult queryResetResult()throws Exception{
        int request=request();String id,token,binding;
        synchronized(this){if(operationId==null || claimedAt+86400000L<=System.currentTimeMillis())throw new IllegalStateException("This recovery result is no longer available. Sign in or start again.");id=operationId;token=grant;binding=verifier;}
        JSONObject value=transport.recover(new JSONObject().put("action","result").put("reset_grant",token).put("code_verifier",binding).put("operation_id",id));current(request);return acceptResult(value,id,request);
    }
    private RecoveryData.ResetResult acceptResult(JSONObject value,String id,int request){
        RecoveryData.ResetResult result=RecoveryData.resetResult(value,id);
        synchronized(this){current(request);resultState=result.state;if("failed".equals(result.state))clearReset();}return result;
    }
    public SessionProfile completePasswordSignIn(String password)throws Exception{
        int request=request();String account;
        synchronized(this){
            if(!"completed".equals(resultState) || signingIn)throw new IllegalStateException("Confirm the password reset before signing in.");
            if(passwordDigest==null || !MessageDigest.isEqual(passwordDigest,digest(password)))throw new IllegalArgumentException("Use the password entered for this reset.");
            signingIn=true;account=email;
        }
        SessionProfile result=null;
        try{result=transport.signInPassword(account,password);current(request);if(result==null || result.email==null || !account.equalsIgnoreCase(result.email.trim()))throw changed();return result;}
        catch(Exception failure){if(result!=null)transport.discardSignIn(result);throw failure;}
        finally{synchronized(this){clearReset();finished=true;}}
    }
    private static void validatePassword(String password){if(AccountValidation.passwordError(password,password)!=null || password.getBytes(StandardCharsets.UTF_8).length>72)throw new IllegalArgumentException("Use at least six characters with a letter, a decimal digit and a punctuation or symbol, up to 72 UTF-8 bytes.");}
    private static byte[] digest(String value)throws Exception{return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));}
    private void clearReset(){email=null;grant=null;verifier=null;operationId=null;resultState=null;grantExpiresAt=0;claimedAt=0;if(passwordDigest!=null)Arrays.fill(passwordDigest,(byte)0);passwordDigest=null;}
    @Override public synchronized void close(){closed=true;++generation;offeredGeneration=null;clearReset();}
    private static IllegalStateException changed(){return new IllegalStateException("This account or recovery session has changed. Start again.");}
}
