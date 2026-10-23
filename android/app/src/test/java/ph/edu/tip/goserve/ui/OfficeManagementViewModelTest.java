package ph.edu.tip.goserve.ui;

import android.app.Application;
import androidx.lifecycle.SavedStateHandle;
import java.util.*;
import java.util.concurrent.Executor;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import ph.edu.tip.goserve.data.OfficeManagementRepository;
import ph.edu.tip.goserve.data.OfficeManagementRepository.*;
import ph.edu.tip.goserve.domain.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34,application=Application.class)
public final class OfficeManagementViewModelTest {
    static final String ACTOR="00000000-0000-4000-8000-000000000090",ID="00000000-0000-4000-8000-000000000001";
    @Test public void retryingTheFirstOfficeReadPopulatesTheActualFields(){F f=new F();f.a.failOfficeRead=true;f.vm.start(Kind.OFFICE);f.run();assertEquals("NETWORK_ERROR",f.s().error);f.a.failOfficeRead=false;f.vm.retry();f.run();assertEquals(OfficeStatus.OPEN,f.s().status);assertEquals("Actual note",f.s().text);}
    @Test public void cleanOfficeEditorRefreshesCanonicalDataWithoutWriting(){F f=loaded();f.a.version=4;f.vm.refresh();assertEquals(1,f.work.jobs.size());f.run();assertEquals(4,f.s().office.version);assertTrue(f.a.sent.isEmpty());}
    @Test public void explicitReloadKeepsEnteredChangesAndUpdatesTheirBaseVersion(){F f=loaded();f.vm.fields("","Retain my edit","","","",false,false,false);f.a.version=4;f.vm.reloadCurrent();f.run();assertEquals("Retain my edit",f.s().text);assertEquals(4,f.s().office.version);assertTrue(f.a.sent.isEmpty());}
    @Test public void realtimeCannotOverwriteAnEnteredOfficeEdit(){F f=loaded();f.vm.fields("","Unsent note","","","",false,false,false);f.vm.refresh();assertEquals(0,f.work.jobs.size());assertEquals("Unsent note",f.s().text);}
    @Test public void initialOfficeLoadIsQueuedAndNeverWrites(){F f=new F();f.vm.start(Kind.OFFICE);assertTrue(f.s().loading);assertEquals(1,f.work.jobs.size());f.run();assertEquals(OfficeManagementViewModel.Mode.EDIT,f.s().mode);assertEquals("Actual note",f.s().text);assertTrue(f.a.sent.isEmpty());}
    @Test public void reviewAndExplicitConfirmationAreSeparate(){F f=loaded();f.vm.status(OfficeStatus.CUT_OFF_REACHED);f.vm.fields("","Entered note","","","",false,false,false);f.vm.review();assertEquals(OfficeManagementViewModel.Mode.REVIEW,f.s().mode);assertTrue(f.a.sent.isEmpty());f.vm.confirm();assertTrue(f.vm.mutationPending());f.run();assertEquals(1,f.a.sent.size());assertTrue(f.s().saved);assertFalse(f.vm.mutationPending());}
    @Test public void unknownResultLocksExactIntentAndOnlyExplicitRetryReplaysIt(){F f=loaded();f.vm.review();Command c=f.s().command;f.a.failure=new java.io.IOException();f.vm.confirm();f.run();assertTrue(f.s().uncertain);f.vm.fields("","Changed","","","",false,false,false);f.vm.status(OfficeStatus.OPEN);f.vm.refresh();assertEquals("Actual note",f.s().text);assertEquals(0,f.work.jobs.size());f.a.failure=null;f.vm.retry();f.run();assertSame(c,f.a.sent.get(1));assertTrue(f.s().saved);}
    @Test public void staleVersionKeepsFieldsAndRequiresReloadBeforeAnotherReview(){F f=loaded();f.vm.fields("","Keep this note","","","",false,false,false);f.vm.review();f.a.failure=new ph.edu.tip.goserve.data.SupabaseClient.ApiException(409,"STALE_VERSION","changed");f.vm.confirm();f.run();assertTrue(f.s().stale);assertFalse(f.s().uncertain);f.vm.review();assertEquals(OfficeManagementViewModel.Mode.EDIT,f.s().mode);f.a.version=4;f.vm.refresh();f.run();assertEquals("Keep this note",f.s().text);assertEquals(4,f.s().office.version);f.vm.review();assertEquals(4,f.s().command.version);}
    @Test public void realtimeDuringReviewCannotDropThePreparedCommand(){F f=loaded();f.vm.review();Command c=f.s().command;f.vm.refresh();f.vm.refresh();assertSame(c,f.s().command);assertEquals(0,f.work.jobs.size());}
    @Test public void newAnnouncementAndCatalogueNeverPublishOnCreation(){for(Kind kind:List.of(Kind.ANNOUNCEMENT,Kind.SERVICE)){F f=new F();f.vm.start(kind);f.run();f.vm.create();assertEquals(OfficeManagementViewModel.Mode.EDIT,f.s().mode);assertFalse(f.s().active);assertTrue(f.a.sent.isEmpty());assertEquals("",f.s().name);}}
    @Test public void restoredUnknownCommandRereadsButNeverAutomaticallyWrites()throws Exception{F f=loaded();f.vm.fields("","Restore exact note","","","",false,false,false);f.vm.review();f.a.failure=new java.io.IOException();f.vm.confirm();f.run();Command c=f.s().command;Q work=new Q(),ui=new Q();OfficeManagementViewModel restored=new OfficeManagementViewModel(RuntimeEnvironment.getApplication(),f.a,work,ui,f.saved);restored.start(Kind.OFFICE);work.run();ui.run();assertEquals(1,f.a.sent.size());assertEquals("Restore exact note",restored.state().getValue().text);assertTrue(restored.state().getValue().uncertain);f.a.failure=null;restored.retry();work.run();ui.run();assertEquals(c.key,f.a.sent.get(1).key);assertEquals(c.arguments().toString(),wire(f.a.sent.get(1)));}
    @Test public void accountChangeBeforePublicationErasesRowsFieldsAndSavedRetry(){F f=loaded();f.vm.review();f.vm.confirm();f.work.run();f.a.current=false;f.ui.run();assertTrue(f.s().blocked);assertEquals("",f.s().text);assertNull(f.s().office);assertNull(f.s().command);assertTrue(f.saved.keys().isEmpty());}
    @Test public void restoreUnderAnotherAccountErasesPrivateDraftBeforeRendering(){F f=loaded();f.vm.fields("","Private draft","","","",false,false,false);f.a.actor="00000000-0000-4000-8000-000000000099";OfficeManagementViewModel other=new OfficeManagementViewModel(RuntimeEnvironment.getApplication(),f.a,new Q(),new Q(),f.saved);assertEquals("",other.state().getValue().text);assertTrue(f.saved.keys().isEmpty());}
    @Test public void invalidDraftCannotEnterReviewOrWrite(){F f=loaded();f.a.draftFailure=true;f.vm.review();assertEquals(OfficeManagementViewModel.Mode.EDIT,f.s().mode);assertEquals("VALIDATION_FAILED",f.s().error);assertTrue(f.a.sent.isEmpty());}
    @Test public void localDiscardDoesNotUndoOrRepeatAnUnknownSave(){F f=loaded();f.vm.review();f.a.failure=new java.io.IOException();f.vm.confirm();f.vm.discardLocalDraft();assertTrue(f.vm.mutationPending());f.run();f.vm.discardLocalDraft();assertFalse(f.vm.mutationPending());assertEquals(1,f.a.sent.size());assertNull(f.s().command);}
    @Test public void invalidationsWhileLoadingCoalesceToOneMoreRead(){F f=new F();f.vm.start(Kind.SERVICE);f.vm.refresh();f.vm.refresh();f.run();assertEquals(1,f.work.jobs.size());f.run();assertEquals(2,f.a.loads);}
    static String wire(Command c){try{return c.arguments().toString();}catch(Exception e){throw new AssertionError(e);}}
    static F loaded(){F f=new F();f.vm.start(Kind.OFFICE);f.run();return f;}
    static final class Q implements Executor{final ArrayDeque<Runnable> jobs=new ArrayDeque<>();public void execute(Runnable r){jobs.add(r);}void run(){jobs.remove().run();}}
    static final class F{final A a=new A();final Q work=new Q(),ui=new Q();final SavedStateHandle saved=new SavedStateHandle();final OfficeManagementViewModel vm=new OfficeManagementViewModel(RuntimeEnvironment.getApplication(),a,work,ui,saved);OfficeManagementViewModel.State s(){return vm.state().getValue();}void run(){work.run();ui.run();}}
    static class A implements OfficeManagementViewModel.Actions{
        String actor=ACTOR;boolean current=true,draftFailure,failOfficeRead;int loads,key;long version=3;Exception failure;final List<Command> sent=new ArrayList<>();
        public boolean allowed(){return true;}public String accountId(){return actor;}public String officeId(){return "OFF-REG";}public void requireCurrent(){if(!current)throw new IllegalStateException("changed");}
        public Info office()throws Exception{if(failOfficeRead)throw new java.io.IOException();return new Info("OFF-REG","Registrar","Actual note",OfficeStatus.OPEN,version,null,null);}
        public Page<Announcement> announcements(String t,String id){loads++;return new Page<>(List.of(),null,null);}public Page<Catalog> services(String id){loads++;return new Page<>(List.of(),null,null);}
        public Announcement announcement(String id){return new Announcement(id,"OFF-REG","Actual announcement",ACTOR,"2026-10-22T00:00:00Z",true,1);}public Catalog service(String id){return null;}
        public Command draft(OfficeManagementViewModel.State s,Command previous)throws Exception{if(draftFailure)throw new WorkflowException("VALIDATION_FAILED");return new Command(s.kind,actor,"OFF-REG",s.kind==Kind.OFFICE?"OFF-REG":null,s.kind==Kind.OFFICE?s.office.version:0,"key"+(++key),new JSONObject().put("note",s.text).toString());}
        public Command restore(Kind kind,String id,long v,String key,String body){return new Command(kind,actor,"OFF-REG",id,v,key,body);}
        public Result save(Command c)throws Exception{sent.add(c);if(failure!=null)throw failure;return new Result(new Info("OFF-REG","Registrar",c.arguments().getString("note"),OfficeStatus.OPEN,version+1,"2026-10-22T00:00:00Z",actor),null,null);}
    }
}
