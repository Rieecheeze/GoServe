package ph.edu.tip.goserve.ui;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import ph.edu.tip.goserve.data.SupabaseClient;
import ph.edu.tip.goserve.domain.RecoveryData;
import ph.edu.tip.goserve.domain.Role;
import ph.edu.tip.goserve.domain.SessionProfile;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public final class RecoverySecurityViewModelTest {
    @Test public void previewReadsNoCodesAndCannotCreateOrResetAnything(){Fixture f=new Fixture();f.vm.start(RecoverySecurityViewModel.Kind.SETUP,true);f.vm.primary();assertEquals(0,f.actions.checks);assertEquals(0,f.work.tasks.size());assertEquals("PREVIEW",f.state().error);assertNull(f.state().codes);}
    @Test public void mandatorySetupNeedsExplicitCodeGenerationThenExplicitSaving(){Fixture f=new Fixture();f.vm.start(RecoverySecurityViewModel.Kind.SETUP,false);f.finish();assertEquals(RecoverySecurityViewModel.Page.STATUS,f.state().page);assertEquals(0,f.actions.generations);f.vm.primary();f.finish();assertEquals(RecoverySecurityViewModel.Page.CODES,f.state().page);assertEquals(10,f.state().codes.codes.size());assertEquals(0,f.actions.acknowledgments);f.vm.primary();f.finish();assertEquals(1,f.actions.acknowledgments);assertEquals(RecoverySecurityViewModel.Page.COMPLETE,f.state().page);assertNull(f.state().codes);}
    @Test public void laterManagementShowsOnlyCountAndRequiresRecentNormalSignInForGeneration(){Fixture f=new Fixture();f.actions.status=new RecoveryData.Status(true,true,7,"set",false);f.vm.start(RecoverySecurityViewModel.Kind.CODES,false);f.finish();assertEquals(7,f.state().status.remainingCodes);assertNull(f.state().codes);f.actions.generateFailure=new SupabaseClient.ApiException(403,"RECENT_AUTH_REQUIRED","private");f.vm.primary();f.finish();assertEquals("RECENT_AUTH_REQUIRED",f.state().error);assertNull(f.state().codes);assertEquals(1,f.actions.generations);assertEquals("",f.vm.proof());}
    @Test public void lostCodeResponseNeverAutomaticallyRepeatsGenerationOrAcknowledgesUnknownCodes(){Fixture f=new Fixture();f.vm.start(RecoverySecurityViewModel.Kind.SETUP,false);f.finish();f.actions.generateFailure=new IOException();f.vm.primary();f.finish();assertEquals("CODES_UNCONFIRMED",f.state().error);assertNull(f.state().codes);assertEquals(1,f.actions.generations);assertEquals(0,f.actions.acknowledgments);f.actions.status=new RecoveryData.Status(true,false,10,"set",true);f.vm.primary();f.finish();assertEquals(1,f.actions.generations);assertEquals(RecoverySecurityViewModel.Page.STATUS,f.state().page);}
    @Test public void pendingPasswordMutationOnlyQueriesResultThenSignsInNormally(){Fixture f=new Fixture();f.vm.start(RecoverySecurityViewModel.Kind.FORGOT,false);f.vm.editEmail("student@tip.edu.ph");f.vm.editProof("saved-own-code");f.vm.primary();f.finish();assertEquals("saved-own-code",f.actions.proof);assertEquals(RecoverySecurityViewModel.Page.PASSWORD,f.state().page);String password=" New 1! password ";f.vm.editPasswords(password,password);f.vm.primary();f.vm.primary();f.finish();assertEquals(RecoverySecurityViewModel.Page.PENDING,f.state().page);assertEquals(1,f.actions.resets);f.vm.primary();f.finish();assertEquals(1,f.actions.resets);assertEquals(1,f.actions.queries);assertEquals(password,f.actions.signedInPassword);assertNotNull(f.state().signedIn);assertEquals("",f.vm.password());}
    @Test public void latePrivateCodesCannotPublishAfterAccountReplacement(){Fixture f=new Fixture();f.vm.start(RecoverySecurityViewModel.Kind.SETUP,false);f.finish();f.vm.primary();f.work.run();f.actions.current=false;f.ui.run();assertEquals(RecoverySecurityViewModel.Page.UNAVAILABLE,f.state().page);assertNull(f.state().codes);assertNull(f.state().status);}
    @Test public void exhaustedCodeSetCannotCompleteMandatorySetup(){Fixture f=new Fixture();f.actions.status=new RecoveryData.Status(true,true,0,"set",true);f.vm.start(RecoverySecurityViewModel.Kind.SETUP,false);f.finish();assertEquals(RecoverySecurityViewModel.Page.STATUS,f.state().page);assertEquals(0,f.actions.generations);}
    @Test public void invalidNewPasswordCannotClaimResetAndClearingDiscardsAllSecrets(){Fixture f=new Fixture();f.vm.start(RecoverySecurityViewModel.Kind.FORGOT,false);f.vm.editEmail("student@tip.edu.ph");f.vm.editProof("saved-own-code");f.vm.primary();f.finish();f.vm.editPasswords("short","short");f.vm.primary();assertEquals("PASSWORD_WEAK",f.state().error);assertEquals(0,f.actions.resets);f.vm.abandon();assertEquals("",f.vm.password());assertEquals("",f.vm.proof());assertEquals("",f.vm.email());assertNull(f.state().codes);}
    static final class Queue implements Executor{final ArrayDeque<Runnable> tasks=new ArrayDeque<>();public void execute(Runnable task){tasks.add(task);}void run(){tasks.remove().run();}}
    static final class Actions implements RecoverySecurityViewModel.Actions{
        int checks,acknowledgments,generations,resets,queries;boolean current=true;String proof,signedInPassword;Exception generateFailure;
        RecoveryData.Status status=new RecoveryData.Status(false,false,0,null,true);
        public void requireCurrent(){checks++;if(!current)throw new IllegalStateException();}public String email(){return "student@tip.edu.ph";}public RecoveryData.Status status(){return status;}
        public RecoveryData.Codes generate()throws Exception{generations++;if(generateFailure!=null)throw generateFailure;return codes();}
        public RecoveryData.Status acknowledge(String generation){acknowledgments++;return new RecoveryData.Status(true,true,10,"set",false);}
        public void verify(String email,String code){proof=code;}public RecoveryData.ResetResult reset(String password){resets++;return new RecoveryData.ResetResult("pending","operation");}public RecoveryData.ResetResult result(){queries++;return new RecoveryData.ResetResult("completed","operation");}
        public SessionProfile signIn(String password){signedInPassword=password;return new SessionProfile("owner","student@tip.edu.ph","Student",Role.STUDENT,null,null,false,true);}public void close(){}
        private RecoveryData.Codes codes(){List<String> values=new ArrayList<>();for(int index=0;index<10;index++)values.add("AAAA-AAAA-AAAA-AAAA-AAAA-AAAA-AAAA-AAA"+(char)('A'+index));return new RecoveryData.Codes("set",values,10);}
    }
    static final class Fixture{final Queue work=new Queue(),ui=new Queue();final Actions actions=new Actions();final RecoverySecurityViewModel vm=new RecoverySecurityViewModel(RuntimeEnvironment.getApplication(),actions,work,ui);RecoverySecurityViewModel.State state(){return vm.state().getValue();}void finish(){for(int count=0;count<20&&(work.tasks.size()>0||ui.tasks.size()>0);count++){while(!work.tasks.isEmpty())work.run();while(!ui.tasks.isEmpty())ui.run();}}}
}