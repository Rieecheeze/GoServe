package ph.edu.tip.goserve.data;

import android.net.Uri;
import java.util.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import ph.edu.tip.goserve.domain.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34,application=android.app.Application.class)
public final class OfficeManagementRepositoryTest {
    static final String ACCOUNT=id(90),SERVICE=id(1),NOTICE=id(2),TIME="2026-10-22T00:00:00Z";
    @Test public void ownOfficeReadUsesOnlyExplicitColumnsAndPinnedIdentity()throws Exception{
        Wire w=new Wire();OfficeManagementRepository.Info o=w.repo.office();assertEquals("OFF-REG",o.id);assertEquals(3,o.version);Uri path=Uri.parse(w.paths.get(0));assertEquals("eq.OFF-REG",path.getQueryParameter("id"));assertFalse(path.getQueryParameter("select").contains("*"));
    }
    @Test public void anotherOfficeResponseIsRejectedBeforeItCanBeEdited()throws Exception{
        Wire w=new Wire();w.office.put("id","OFF-ACC");reject(()->w.repo.office());assertTrue(w.sent.isEmpty());
    }
    @Test public void officeCommandUsesTheFourExactArgumentsAndStableReplayIdentity()throws Exception{
        Wire w=new Wire();OfficeManagementRepository.Info o=w.repo.office();OfficeManagementRepository.Command c=w.repo.officeDraft(o,OfficeStatus.ON_LUNCH_BREAK,"Actual note",null);
        assertSame(c,w.repo.officeDraft(o,OfficeStatus.ON_LUNCH_BREAK,"Actual note",c));JSONObject args=c.arguments();assertEquals(Set.of("p_expected_version","p_status","p_note","p_idempotency_key"),keys(args));assertEquals(3,args.getLong("p_expected_version"));assertEquals("On Lunch Break",args.getString("p_status"));assertEquals("Actual note",args.getString("p_note"));
        assertNotEquals(c.key,w.repo.officeDraft(o,OfficeStatus.OPEN,"Actual note",c).key);
    }
    @Test public void officeSaveRequiresMatchingReceiptAndCanonicalReread()throws Exception{
        Wire w=new Wire();OfficeManagementRepository.Command c=w.repo.officeDraft(w.repo.office(),OfficeStatus.TEMPORARILY_CLOSED,"Closed for this office",null);w.paths.clear();OfficeManagementRepository.Result result=w.repo.save(c);
        assertEquals("update_office",w.operations.get(0));assertEquals(4,result.office.version);assertEquals(OfficeStatus.TEMPORARILY_CLOSED,result.office.status);assertEquals(ACCOUNT,result.office.changedBy);assertEquals(1,w.paths.size());
    }
    @Test public void malformedPostWriteReceiptCannotBecomeSaved()throws Exception{
        Wire w=new Wire();OfficeManagementRepository.Command c=w.repo.officeDraft(w.repo.office(),OfficeStatus.OPEN,"note",null);w.malformed=true;reject(()->w.repo.save(c));assertEquals(1,w.sent.size());
    }
    @Test public void announcementReadsAreBoundedFilteredAndCursorOrdered()throws Exception{
        Wire w=new Wire();OfficeManagementRepository.Page<OfficeManagementRepository.Announcement> page=w.repo.announcements(null,null,true);assertEquals(1,page.items.size());Uri p=Uri.parse(w.paths.get(0));assertEquals("eq.OFF-REG",p.getQueryParameter("office_id"));assertEquals("eq.true",p.getQueryParameter("active"));assertEquals("50",p.getQueryParameter("limit"));assertEquals("created_at.desc,id.desc",p.getQueryParameter("order"));
    }
    @Test public void newAnnouncementDoesNotWriteUntilConfirmedAndDoesNotForgeActor()throws Exception{
        Wire w=new Wire();OfficeManagementRepository.Command c=w.repo.announcementDraft(null,"Preserve punctuation?!\nOffice update.",true,null);assertTrue(w.sent.isEmpty());JSONObject a=c.arguments();assertEquals(Set.of("p_id","p_expected_version","p_body","p_active","p_idempotency_key"),keys(a));assertTrue(a.isNull("p_id"));assertEquals(0,a.getLong("p_expected_version"));
        OfficeManagementRepository.Result r=w.repo.save(c);assertEquals(NOTICE,r.announcement.id);assertEquals(ACCOUNT,r.announcement.actorId);assertEquals("save_announcement",w.operations.get(0));
    }
    @Test public void inactiveServiceReadAndRequiredFeePreserveExistingRequirementProperties()throws Exception{
        Wire w=new Wire();OfficeManagementRepository.Catalog original=w.repo.services(null).items.get(0);assertFalse(Uri.parse(w.paths.get(0)).getQueryParameterNames().contains("active"));
        OfficeManagementRepository.Command c=w.repo.serviceDraft(original,"Entered service",List.of("digital"),List.of("proof","another document"),FeeDeclaration.REQUIRED,"27.50","ABC",false,null);JSONObject v=c.arguments().getJSONObject("p_value");assertEquals(Set.of("name","delivery_modes","requirements","fee_state","amount","currency","active"),keys(v));assertEquals("27.50",v.getString("amount"));assertEquals("ABC",v.getString("currency"));assertEquals("Preserved label",v.getJSONArray("requirements").getJSONObject(0).getString("label"));assertEquals("another document",v.getJSONArray("requirements").getJSONObject(1).getString("id"));assertTrue(w.sent.isEmpty());
        OfficeManagementRepository.Result result=w.repo.save(c);assertEquals(2,result.service.service.version);assertTrue(result.service.demo);assertEquals("Entered service",result.service.service.name);
    }
    @Test public void freeAndPendingHaveDifferentCanonicalMoneyDeclarations()throws Exception{
        Wire w=new Wire();OfficeManagementRepository.Command free=w.repo.serviceDraft(null,"Free",List.of("physical"),List.of(),FeeDeclaration.FREE,"","",true,null);JSONObject f=free.arguments().getJSONObject("p_value");assertEquals("0",f.get("amount").toString());assertTrue(f.isNull("currency"));
        OfficeManagementRepository.Command pending=w.repo.serviceDraft(null,"Awaiting declaration",List.of("digital"),List.of(),FeeDeclaration.PENDING_REVIEW,"","",true,null);JSONObject p=pending.arguments().getJSONObject("p_value");assertTrue(p.isNull("amount"));assertTrue(p.isNull("currency"));
    }
    @Test public void invalidFeeModesAndBlankAnnouncementCannotProduceCommands()throws Exception{
        Wire w=new Wire();reject(()->w.repo.serviceDraft(null,"Service",List.of("digital"),List.of(),FeeDeclaration.REQUIRED,"NaN","PHP",true,null));reject(()->w.repo.serviceDraft(null,"Service",List.of(),List.of(),FeeDeclaration.FREE,"","",true,null));reject(()->w.repo.announcementDraft(null,"  ",true,null));assertTrue(w.sent.isEmpty());
    }
    @Test public void moneyFormattingRejectsExponentExpansionBeyondTheFormResourceLimit()throws Exception{
        Wire w=new Wire();
        for(String amount:List.of("1e20001","1e-20001"))reject(()->w.repo.serviceDraft(null,"Service",List.of("digital"),List.of(),FeeDeclaration.REQUIRED,amount,"PHP",false,null));
        JSONObject exact=w.repo.serviceDraft(null,"Service",List.of("digital"),List.of(),FeeDeclaration.REQUIRED,"2.75e1","PHP",false,null).arguments().getJSONObject("p_value");
        assertEquals("27.5",exact.getString("amount"));assertTrue(w.sent.isEmpty());
    }
    @Test public void accountReplacementRejectsQueuedOrRestoredCommandWithoutWriting()throws Exception{
        Wire w=new Wire();OfficeManagementRepository.Command c=w.repo.announcementDraft(null,"Update",true,null);w.current=false;reject(()->w.repo.save(c));assertTrue(w.sent.isEmpty());
        Wire other=new Wire();other.account=id(99);reject(()->other.repo.save(c));assertTrue(other.sent.isEmpty());
    }
    @Test public void duplicateRetryReusesExactWireKeyAndRereadsTheSavedRecord()throws Exception{
        Wire w=new Wire();OfficeManagementRepository.Command c=w.repo.announcementDraft(null,"Exact update",true,null);w.repo.save(c);w.repo.save(c);assertEquals(w.sent.get(0).toString(),w.sent.get(1).toString());assertEquals(1,w.audit);assertEquals(2,w.paths.size());
    }
    interface Throwing{Object run()throws Exception;}static void reject(Throwing action)throws Exception{try{action.run();fail("Expected rejection");}catch(OfficeManagementRepository.WorkflowException|IllegalStateException expected){}}
    static String id(int n){return String.format(Locale.ROOT,"00000000-0000-4000-8000-%012d",n);}
    static Set<String> keys(JSONObject object){Set<String> values=new HashSet<>();object.keys().forEachRemaining(values::add);return values;}
    static final class Wire implements OfficeManagementRepository.Transport {
        String account=ACCOUNT;boolean current=true,malformed;int audit;final List<String> paths=new ArrayList<>(),operations=new ArrayList<>();final List<JSONObject> sent=new ArrayList<>();final Map<String,JSONObject> receipts=new HashMap<>();
        JSONObject office=new JSONObject(),service=new JSONObject(),notice=new JSONObject();final OfficeManagementRepository repo=new OfficeManagementRepository(this,"OFF-REG");
        Wire()throws Exception{
            office.put("id","OFF-REG").put("name","Registrar").put("status","Open").put("note","").put("version",3).put("status_changed_at",JSONObject.NULL).put("status_changed_by",JSONObject.NULL);
            service.put("id",SERVICE).put("office_id","OFF-REG").put("name","Existing").put("delivery_modes",new JSONArray().put("physical")).put("requirements",new JSONArray().put(new JSONObject().put("id","proof").put("label","Preserved label"))).put("fee_state","pending").put("amount",JSONObject.NULL).put("currency",JSONObject.NULL).put("active",false).put("version",1).put("is_demo",true);
            notice.put("id",NOTICE).put("office_id","OFF-REG").put("body","Existing notice").put("active",true).put("version",1).put("actor_id",ACCOUNT).put("created_at",TIME);
        }
        public String accountId(){return account;}public void requireCurrent(){if(!current)throw new IllegalStateException("changed");}
        public JSONArray get(String path)throws Exception{paths.add(path);return new JSONArray().put(new JSONObject((path.startsWith("/rest/v1/offices")?office:path.startsWith("/rest/v1/announcements")?notice:service).toString()));}
        public JSONObject rpc(String name,JSONObject args)throws Exception{
            operations.add(name);sent.add(new JSONObject(args.toString()));String key=args.getString("p_idempotency_key");if(receipts.containsKey(key))return new JSONObject(receipts.get(key).toString());audit++;
            JSONObject r;
            if("update_office".equals(name)){office.put("status",args.getString("p_status")).put("note",args.getString("p_note")).put("version",4).put("status_changed_at",TIME).put("status_changed_by",ACCOUNT);r=office;}
            else if("save_announcement".equals(name)){notice.put("body",args.getString("p_body")).put("active",args.getBoolean("p_active")).put("version",args.isNull("p_id")?1:args.getLong("p_expected_version")+1);r=notice;}
            else {JSONObject v=args.getJSONObject("p_value");for(String k:keys(v))service.put(k,v.get(k));service.put("version",args.isNull("p_id")?1:args.getLong("p_expected_version")+1);r=service;}
            JSONObject receipt=new JSONObject(r.toString());if(malformed)receipt.put("version",0);receipts.put(key,receipt);return new JSONObject(receipt.toString());
        }
    }
}
