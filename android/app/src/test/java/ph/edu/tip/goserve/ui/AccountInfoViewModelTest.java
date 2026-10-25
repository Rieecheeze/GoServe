package ph.edu.tip.goserve.ui;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.data.SupabaseClient;
import ph.edu.tip.goserve.domain.Role;
import ph.edu.tip.goserve.domain.SessionProfile;
import ph.edu.tip.goserve.domain.StudentProfile;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public final class AccountInfoViewModelTest {
    @Test public void previewNeverReadsAnUnderlyingAccountOrEnablesSaving(){Fixture f=new Fixture();f.vm.start(true);assertEquals(0,f.actions.reads);assertFalse(f.vm.canEdit());assertFalse(f.vm.canSave());assertNull(f.vm.state().getValue().profile);assertEquals("",f.vm.draft().firstName);}
    @Test public void validationPreventsWritesAndUncertainRetryKeepsOneExactOperation(){Fixture f=new Fixture();f.vm.start(false);f.vm.setDraft(student(""));f.vm.save();assertEquals(0,f.work.tasks.size());assertTrue(f.vm.state().getValue().fieldErrors.containsKey("first_name"));f.vm.setDraft(student("Verbatim  Name"));f.actions.failure=new IOException("private");f.vm.save();f.vm.save();assertEquals(1,f.work.tasks.size());f.finish();AccountInfoViewModel.Update held=f.actions.update;assertEquals("Verbatim  Name",held.profile.firstName);assertFalse(f.vm.canEdit());assertTrue(f.vm.canSave());f.actions.failure=null;f.vm.save();f.finish();assertSame(held,f.actions.update);assertTrue(f.vm.state().getValue().saved);}
    @Test public void staleVersionLoadsCanonicalDetailsForReviewAndDoesNotReplayTheOldUpdate(){Fixture f=new Fixture();f.vm.start(false);f.vm.setDraft(student("Draft"));f.actions.failure=new SupabaseClient.ApiException(409,"STALE_VERSION","private");f.vm.save();f.actions.profile=profile(student("Canonical"),8);f.finish();assertEquals("Canonical",f.vm.draft().firstName);assertEquals(R.string.account_info_stale,f.vm.state().getValue().message);assertFalse(f.vm.canSave());assertTrue(f.vm.canEdit());}
    @Test public void accountLossBeforePublicationClearsPrivateDraftAndResult(){Fixture f=new Fixture();f.vm.start(false);f.vm.setDraft(student("Private draft"));f.vm.save();f.work.run();f.actions.current=false;f.ui.run();assertNull(f.vm.state().getValue().profile);assertEquals("",f.vm.draft().firstName);assertFalse(f.vm.canSave());assertFalse(f.vm.state().getValue().saved);}
    @Test public void staffProfileIsReadOnlyAndCannotCallStudentSave(){Fixture f=new Fixture();f.actions.profile=new SessionProfile("account","office@example.com","Office",Role.STAFF,"office",null,false,1,true,"v1","v1","home",null);f.vm.start(false);assertFalse(f.vm.canEdit());f.vm.save();assertEquals(0,f.work.tasks.size());}
    static StudentProfile student(String name){return new StudentProfile(name,"",true,"Cruz","TEST-ID","Program","Year","Mobile","Gender");}
    static SessionProfile profile(StudentProfile student,long version){return new SessionProfile("account","student@example.com",student.firstName,Role.STUDENT,null,null,false,version,true,"v1","v1","home",student);}
    static final class Queue implements Executor{final ArrayDeque<Runnable> tasks=new ArrayDeque<>();public void execute(Runnable task){tasks.add(task);}void run(){tasks.remove().run();}}
    static final class Actions implements AccountInfoViewModel.Actions{
        boolean current=true;int reads;Exception failure;AccountInfoViewModel.Update update;SessionProfile profile=profile(student("Saved"),1);
        public void requireCurrent(){if(!current)throw new IllegalStateException();}
        public SessionProfile current(){reads++;return profile;}
        public SessionProfile save(AccountInfoViewModel.Update value)throws Exception{update=value;if(failure!=null)throw failure;profile=profile(value.profile,value.expectedVersion+1);return profile;}
    }
    static final class Fixture{final Queue work=new Queue(),ui=new Queue();final Actions actions=new Actions();final AccountInfoViewModel vm=new AccountInfoViewModel(RuntimeEnvironment.getApplication(),actions,work,ui);void finish(){work.run();ui.run();}}
}
