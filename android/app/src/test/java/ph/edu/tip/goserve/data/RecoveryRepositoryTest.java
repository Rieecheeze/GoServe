package ph.edu.tip.goserve.data;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import ph.edu.tip.goserve.domain.RecoveryData;
import ph.edu.tip.goserve.domain.Role;
import ph.edu.tip.goserve.domain.SessionProfile;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public final class RecoveryRepositoryTest {
    static final String ID="884cd499-a440-48ef-a6ef-b8ddfe1bc304",CODE="AAAA-AAAA-AAAA-AAAA-AAAA-AAAA-AAAA-AAAA",GRANT="a".repeat(43),PASSWORD="Valid 2! password";
    static String expiresAfter(long seconds){return Instant.ofEpochMilli(System.currentTimeMillis()+seconds*1000).toString();}
    static JSONObject status(boolean acknowledged)throws Exception{return new JSONObject().put("codes_configured",true).put("codes_acknowledged",acknowledged).put("remaining_codes",10).put("generation",ID).put("requires_setup",!acknowledged);}
    static JSONObject codes()throws Exception{JSONArray values=new JSONArray();for(char letter='A';letter<='J';letter++)values.put((""+letter).repeat(4)+"-AAAA-AAAA-AAAA-AAAA-AAAA-AAAA-AAAA");return new JSONObject().put("generation",ID).put("codes",values).put("remaining_codes",10).put("codes_configured",true).put("codes_acknowledged",false);}
    static final class Server implements RecoveryRepository.Transport {
        boolean current=true,loseReset,replaceOwner,expiredGrant,wrongOperation;String jobState="completed";int mutations,signins;JSONObject last;
        final List<JSONObject> publicCalls=new ArrayList<>();
        public void requireCurrent(){if(!current)throw new IllegalStateException("Account changed");}
        public JSONObject control(JSONObject input)throws Exception{last=new JSONObject(input.toString());if(replaceOwner)current=false;switch(input.getString("action")){case "status":return status(false);case "acknowledge_codes":return status(true);default:return codes();}}
        public JSONObject recover(JSONObject input)throws Exception{last=new JSONObject(input.toString());publicCalls.add(last);if(replaceOwner)current=false;if("verify".equals(input.getString("action")))return new JSONObject().put("reset_grant",GRANT).put("expires_at",expiresAfter(expiredGrant?-1:240));if("reset".equals(input.getString("action"))){mutations++;if(loseReset)throw new IOException("Private provider detail");}return new JSONObject().put("state",jobState).put("operation_id",wrongOperation?"884cd499-a440-48ef-a6ef-b8ddfe1bc305":input.getString("operation_id"));}
        public SessionProfile signInPassword(String email,String password){signins++;return new SessionProfile(ID,email,"Ana",Role.STUDENT,null,null,false,1,true,"v1","v1","setup_recovery",null);}
    }
    @Test public void unknownFieldsContradictoryStatusAndDuplicateCodesFailClosed()throws Exception {
        assertThrows(IllegalArgumentException.class,()->RecoveryData.status(status(false).put("owner","other")));
        assertThrows(IllegalArgumentException.class,()->RecoveryData.status(status(false).put("requires_setup",false)));
        JSONObject duplicate=codes();duplicate.getJSONArray("codes").put(1,duplicate.getJSONArray("codes").getString(0));
        assertThrows(IllegalArgumentException.class,()->RecoveryData.codes(duplicate));
        assertTrue(RecoveryData.status(status(false)).requiresSetup);
    }
    @Test public void exhaustedCodesRequireNewSetupAndNoPlaintextCanBeRetrieved()throws Exception {
        assertTrue(RecoveryData.status(status(true).put("remaining_codes",0).put("requires_setup",true)).requiresSetup);
        assertThrows(IllegalArgumentException.class,()->RecoveryData.status(status(true).put("remaining_codes",0)));
        Server server=new Server();RecoveryRepository repository=new RecoveryRepository(server);repository.status();assertEquals(1,server.last.length());assertFalse(server.last.has("codes"));
    }
    @Test public void verificationSendsOnlyChallengeAndKeepsVerifierBoundToItsReset()throws Exception {
        Server server=new Server();RecoveryRepository repository=new RecoveryRepository(server);repository.verifyProof(" ana@tip.edu.ph ",CODE);
        JSONObject verify=server.publicCalls.get(0);assertEquals(5,verify.length());assertEquals("ana@tip.edu.ph",verify.getString("email"));assertFalse(verify.has("code_verifier"));
        assertTrue(verify.getString("code_challenge").matches("[A-Za-z0-9_-]{43}"));
        assertEquals("completed",repository.resetPassword(PASSWORD).state);JSONObject reset=server.last;assertEquals(5,reset.length());assertEquals(PASSWORD,reset.getString("password"));
        String challenge=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(reset.getString("code_verifier").getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        assertEquals(challenge,verify.getString("code_challenge"));assertEquals(1,server.mutations);
    }
    @Test public void uncertainResetCanOnlyQueryItsOriginalJobAndCannotRepeatAMutation()throws Exception {
        Server server=new Server();server.loseReset=true;RecoveryRepository repository=new RecoveryRepository(server);repository.verifyProof("ana@tip.edu.ph",CODE);
        RecoveryData.ResetResult pending=repository.resetPassword(PASSWORD);assertEquals("pending",pending.state);assertThrows(IllegalStateException.class,()->repository.resetPassword(PASSWORD));
        assertEquals("completed",repository.queryResetResult().state);assertEquals("result",server.last.getString("action"));assertEquals(pending.operationId,server.last.getString("operation_id"));assertEquals(1,server.mutations);
        SessionProfile profile=repository.completePasswordSignIn(PASSWORD);assertEquals("setup_recovery",profile.nextAction);assertEquals(1,server.signins);repository.requireCurrent();
        assertThrows(IllegalStateException.class,()->repository.completePasswordSignIn(PASSWORD));
    }
    @Test public void acknowledgementRequiresCodesActuallyReturnedInThisFlow()throws Exception {
        Server server=new Server();RecoveryRepository repository=new RecoveryRepository(server);repository.status();assertThrows(IllegalStateException.class,()->repository.acknowledgeCodes(ID));
        RecoveryData.Codes set=repository.generateCodes();
        assertEquals(10,set.codes.size());assertTrue(repository.acknowledgeCodes(set.generation).codesAcknowledged);
    }
    @Test public void ownerReplacementOrClosingCannotPublishSecretsOrIssueMoreRequests()throws Exception {
        Server server=new Server();RecoveryRepository repository=new RecoveryRepository(server);server.replaceOwner=true;
        assertThrows(IllegalStateException.class,repository::generateCodes);assertThrows(IllegalStateException.class,repository::requireCurrent);
        Server other=new Server();RecoveryRepository closed=new RecoveryRepository(other);closed.verifyProof("ana@tip.edu.ph",CODE);closed.close();assertThrows(IllegalStateException.class,()->closed.resetPassword(PASSWORD));assertEquals(0,other.mutations);
    }
    @Test public void expiredGrantCannotAuthorizeAnyPasswordMutation()throws Exception {
        Server server=new Server();server.expiredGrant=true;RecoveryRepository repository=new RecoveryRepository(server);
        assertThrows(IllegalArgumentException.class,()->repository.verifyProof("ana@tip.edu.ph",CODE));
        assertThrows(IllegalStateException.class,()->repository.resetPassword(PASSWORD));assertEquals(0,server.mutations);
    }
    @Test public void overlongEmailIsRejectedBeforeConsumingASavedCode()throws Exception {
        Server server=new Server();RecoveryRepository repository=new RecoveryRepository(server);
        assertThrows(IllegalArgumentException.class,()->repository.verifyProof("a".repeat(244)+"@tip.edu.ph",CODE));assertTrue(server.publicCalls.isEmpty());
    }
    @Test public void unknownStateAndDifferentOperationNeverReleaseSigninOrPermitMutationRetry()throws Exception {
        Server server=new Server();server.jobState="unknown";RecoveryRepository repository=new RecoveryRepository(server);repository.verifyProof("ana@tip.edu.ph",CODE);
        assertEquals("pending",repository.resetPassword(PASSWORD).state);assertThrows(IllegalStateException.class,()->repository.completePasswordSignIn(PASSWORD));
        server.jobState="completed";server.wrongOperation=true;assertThrows(IllegalArgumentException.class,repository::queryResetResult);
        assertThrows(IllegalStateException.class,()->repository.resetPassword(PASSWORD));assertEquals(1,server.mutations);assertEquals(0,server.signins);
    }
    @Test public void invalidPasswordCannotConsumeGrantAndIncompleteJobCannotSignIn()throws Exception {
        Server server=new Server();server.jobState="pending";RecoveryRepository repository=new RecoveryRepository(server);repository.verifyProof("ana@tip.edu.ph",CODE);
        assertThrows(IllegalArgumentException.class,()->repository.resetPassword("weak"));assertEquals(0,server.mutations);
        repository.resetPassword(PASSWORD);assertThrows(IllegalStateException.class,()->repository.completePasswordSignIn(PASSWORD));assertEquals(0,server.signins);
    }
}
