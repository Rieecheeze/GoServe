package ph.edu.tip.goserve.data;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public final class SeryoRepositoryTest {
    private JSONObject state(String consent,boolean ready,String cleanup)throws Exception {
        return new JSONObject().put("consent",consent).put("consent_version",3).put("provider_ready",ready)
                .put("can_send",ready).put("can_attach",ready).put("cleanup",cleanup).put("blockers",new org.json.JSONArray());
    }
    @Test public void attachmentRequiresAllowProviderCapabilityAndClearCleanup()throws Exception {
        assertTrue(SeryoRepository.parse(state("allow",true,"clear")).allowsAttachment());
        for(String consent:new String[]{"unset","decline","revoke"}) assertFalse(SeryoRepository.parse(state(consent,true,"clear")).allowsAttachment());
        assertFalse(SeryoRepository.parse(state("allow",false,"clear")).allowsAttachment());
        assertFalse(SeryoRepository.parse(state("allow",true,"unresolved")).allowsAttachment());
    }
    @Test public void malformedCanonicalFieldsFailClosed()throws Exception {
        for(String field:new String[]{"consent","consent_version","provider_ready","can_send","can_attach","cleanup","blockers"}) {
            JSONObject input=state("allow",true,"clear"); input.remove(field);
            try { SeryoRepository.parse(input); fail(field); } catch(IllegalArgumentException expected) {}
        }
        for(Object value:new Object[]{"3",-1,1.5,JSONObject.NULL}) {
            JSONObject input=state("allow",true,"clear").put("consent_version",value);
            try { SeryoRepository.parse(input); fail("version"); } catch(IllegalArgumentException expected) {}
        }
    }
    @Test public void oneConsentWriteUsesExactReviewedEdgeShapeAndLeaseChecks()throws Exception {
        final int[] calls={0},checks={0}; final JSONObject[] request={null};
        SeryoRepository repo=new SeryoRepository(new SeryoRepository.Transport() {
            public JSONObject control(JSONObject input)throws Exception { calls[0]++;request[0]=input;return state("allow",false,"clear"); }
            public void requireCurrent() { checks[0]++; }
        });
        String key=java.util.UUID.randomUUID().toString();
        assertEquals("allow",repo.consent("allow",2,key).consent);
        assertEquals(1,calls[0]); assertEquals(2,checks[0]); assertEquals(4,request[0].length());
        assertEquals("consent",request[0].getString("kind")); assertEquals(2,request[0].getLong("expected_version")); assertEquals(key,request[0].getString("idempotency_key"));
    }
    @Test public void invalidConsentInputDoesNotReachTransport()throws Exception {
        SeryoRepository repo=new SeryoRepository(new SeryoRepository.Transport() {
            public JSONObject control(JSONObject input) { fail("No transport for invalid input");return null; }
            public void requireCurrent() {}
        });
        for(String decision:new String[]{"unset","ALLOW",""}) try { repo.consent(decision,0,java.util.UUID.randomUUID().toString());fail(); }catch(IllegalArgumentException expected){}
        try { repo.consent("allow",0,"bad-key");fail(); }catch(IllegalArgumentException expected){}
    }
    @Test public void sendUsesOnlyTheQuestionAndOperationKeyAndChecksAccountAfterReply()throws Exception {
        final int[] checks={0};final JSONObject[] command={null};
        SeryoRepository repo=new SeryoRepository(new SeryoRepository.Transport(){
            public JSONObject control(JSONObject input){throw new AssertionError();}
            public JSONObject send(JSONObject input)throws Exception{command[0]=input;return new JSONObject().put("reply","Open Requests.").put("refused",false);}
            public void requireCurrent(){checks[0]++;}
        });
        String key=java.util.UUID.randomUUID().toString();assertEquals("Open Requests.",repo.send("How do requests work?",key));
        assertEquals(2,checks[0]);assertEquals(2,command[0].length());assertEquals(key,command[0].getString("idempotency_key"));
        assertEquals("How do requests work?",command[0].getString("question"));
    }
    @Test public void malformedReplyAndExtraProviderFieldsNeverReachChat()throws Exception {
        for(JSONObject reply:new JSONObject[]{new JSONObject().put("reply",""),new JSONObject().put("reply","Safe").put("refused",false).put("session_id","hidden"),new JSONObject().put("reply",new JSONObject()).put("refused",false)}){
            SeryoRepository repo=new SeryoRepository(new SeryoRepository.Transport(){
                public JSONObject control(JSONObject input){return null;}
                public JSONObject send(JSONObject input){return reply;}
                public void requireCurrent(){}
            });
            try{repo.send("How do requests work?",java.util.UUID.randomUUID().toString());fail();}catch(IllegalArgumentException expected){}
        }
    }
}
