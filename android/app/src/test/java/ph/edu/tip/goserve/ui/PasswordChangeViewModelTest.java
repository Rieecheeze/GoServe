package ph.edu.tip.goserve.ui;

import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import ph.edu.tip.goserve.data.AuthRepository;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public final class PasswordChangeViewModelTest {
    private static final String CURRENT=" Current 1! password ",NEXT=" Next 2! password ";
    @Test public void draftIsRetainedVerbatimWithoutIssuingAnyRequest() {
        Fixture f=new Fixture(); f.edit("123456");
        assertEquals(CURRENT,f.vm.currentPassword()); assertEquals(NEXT,f.vm.newPassword()); assertEquals(NEXT,f.vm.confirmation()); assertEquals("123456",f.vm.code());
        assertEquals(0,f.work.size()); assertEquals(0,f.actions.changes);
    }
    @Test public void submitIsOffMainSingleFlightAndClearsSecretsAfterOwnSignOut() {
        Fixture f=new Fixture(); f.edit(""); f.vm.submit(); f.vm.submit();
        assertEquals(PasswordChangeViewModel.Mode.CHANGING,f.state().mode); assertEquals(0,f.actions.changes); assertEquals(1,f.work.size());
        f.work.run(); assertEquals(1,f.actions.changes); assertEquals(PasswordChangeViewModel.Mode.CHANGING,f.state().mode);
        f.ui.run(); assertEquals(PasswordChangeViewModel.Mode.DONE,f.state().mode);
        assertArrayEquals(new String[]{CURRENT,NEXT,NEXT,""},f.actions.arguments); f.empty();
    }
    @Test public void invalidDraftCannotQueueAnUpdate() {
        Fixture f=new Fixture();
        f.vm.submit(); assertEquals("CURRENT_PASSWORD_REQUIRED",f.state().error);
        f.vm.edit(CURRENT,"weak","weak",""); f.vm.submit(); assertEquals("PASSWORD_WEAK",f.state().error);
        f.vm.edit(CURRENT,NEXT,"different",""); f.vm.submit(); assertEquals("CONFIRMATION_MISMATCH",f.state().error);
        f.vm.edit(CURRENT,CURRENT,CURRENT,""); f.vm.submit(); assertEquals("PASSWORD_REUSED",f.state().error);
        assertEquals(0,f.work.size());
    }
    @Test public void currentPasswordRejectionKeepsDraftAndNeverDisplaysPrivateErrorText() {
        Fixture f=new Fixture(); f.edit(""); f.actions.failure=error("CURRENT_PASSWORD_INVALID"); f.vm.submit(); f.work.run(); f.ui.run();
        assertEquals(PasswordChangeViewModel.Mode.EDITING,f.state().mode); assertEquals("CURRENT_PASSWORD_INVALID",f.state().error);
        assertEquals(CURRENT,f.vm.currentPassword()); assertEquals(NEXT,f.vm.newPassword()); assertFalse(f.state().requiresCode);
    }
    @Test public void reauthenticationNeedsAnExplicitEmailRequestThenACode() {
        Fixture f=new Fixture(); f.edit(""); f.actions.failure=error("REAUTHENTICATION_REQUIRED"); f.vm.submit(); f.work.run(); f.ui.run();
        assertTrue(f.state().requiresCode); assertEquals(0,f.actions.emails); assertEquals(0,f.work.size());
        f.vm.submit(); assertEquals("CODE_REQUIRED",f.state().error); assertEquals(0,f.work.size());
        f.actions.failure=null; f.vm.requestCode(); f.vm.requestCode(); f.vm.submit();
        assertEquals(PasswordChangeViewModel.Mode.SENDING_CODE,f.state().mode); assertEquals(1,f.work.size());
        f.work.run(); f.ui.run(); assertEquals(1,f.actions.emails); assertTrue(f.state().codeSent); assertEquals(NEXT,f.vm.newPassword());
        f.edit("123456"); f.vm.submit(); f.work.run(); f.ui.run();
        assertEquals(PasswordChangeViewModel.Mode.DONE,f.state().mode); assertEquals("123456",f.actions.arguments[3]); f.empty();
    }
    @Test public void codeCannotBeRequestedBeforeSupabaseRequiresIt() {
        Fixture f=new Fixture(); f.vm.requestCode(); assertEquals(0,f.work.size()); assertEquals(0,f.actions.emails);
    }
    @Test public void failedCodeRequestDoesNotClaimDeliveryAndCanBeRetried() {
        Fixture f=new Fixture(); f.requireCode(); f.actions.failure=error("RATE_LIMITED"); f.vm.requestCode(); f.work.run(); f.ui.run();
        assertEquals("RATE_LIMITED",f.state().error); assertFalse(f.state().codeSent); assertEquals(NEXT,f.vm.newPassword());
        f.actions.failure=null; f.vm.requestCode(); f.work.run(); f.ui.run(); assertEquals(2,f.actions.emails); assertTrue(f.state().codeSent);
    }
    @Test public void accountLossBeforeWorkerCannotUseTheOldPasswordOrSendAnEmail() {
        Fixture f=new Fixture(); f.edit(""); f.vm.submit(); f.actions.current=false; f.work.run(); f.ui.run();
        assertEquals(0,f.actions.changes); assertEquals(PasswordChangeViewModel.Mode.UNAVAILABLE,f.state().mode); f.empty();
        f.vm.requestCode(); f.vm.submit(); assertEquals(0,f.work.size());
    }
    @Test public void queuedSuccessCannotPublishForAReplacementAccount() {
        Fixture f=new Fixture(); f.edit(""); f.vm.submit(); f.work.run(); f.actions.ownSignOut=false; f.ui.run();
        assertEquals(PasswordChangeViewModel.Mode.UNAVAILABLE,f.state().mode); f.empty();
    }
    @Test public void uncertainOutcomeClearsDraftInsteadOfOfferingAnotherSubmission() {
        Fixture f=new Fixture(); f.edit(""); f.actions.failure=error("OUTCOME_UNCERTAIN"); f.actions.loseAccountOnFailure=true;
        f.vm.submit(); f.work.run(); f.ui.run(); assertEquals(PasswordChangeViewModel.Mode.UNAVAILABLE,f.state().mode); f.empty();
        f.vm.submit(); assertEquals(0,f.work.size());
    }
    @Test public void stickyPriorAccountInvalidationDoesNotClearTheCurrentDraft() {
        Fixture f=new Fixture(); f.edit(""); f.vm.checkCurrentAccount(); assertEquals(CURRENT,f.vm.currentPassword());
        f.actions.current=false; f.vm.checkCurrentAccount(); assertEquals(PasswordChangeViewModel.Mode.UNAVAILABLE,f.state().mode); f.empty();
    }
    @Test public void clearingViewModelDropsQueuedPublicationAndAllSecrets() {
        Fixture f=new Fixture(); f.edit(""); f.vm.submit(); f.work.run(); f.vm.onCleared(); f.ui.run(); f.empty();
        assertNotEquals(PasswordChangeViewModel.Mode.DONE,f.state().mode);
    }
    private static AuthRepository.PasswordChangeException error(String code) { return new AuthRepository.PasswordChangeException(code,"private provider detail"); }
    private static final class Fixture {
        final Fake actions=new Fake(); final Queue work=new Queue(),ui=new Queue();
        final PasswordChangeViewModel vm=new PasswordChangeViewModel(RuntimeEnvironment.getApplication(),actions,work,ui);
        PasswordChangeViewModel.State state() { return vm.state().getValue(); }
        void edit(String code) { vm.edit(CURRENT,NEXT,NEXT,code); }
        void empty() { assertEquals("",vm.currentPassword()); assertEquals("",vm.newPassword()); assertEquals("",vm.confirmation()); assertEquals("",vm.code()); }
        void requireCode() { edit(""); actions.failure=error("REAUTHENTICATION_REQUIRED"); vm.submit(); work.run(); ui.run(); actions.failure=null; }
    }
    private static final class Queue implements Executor {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>(); public void execute(Runnable task) { tasks.add(task); }
        void run() { assertFalse("Expected queued work",tasks.isEmpty()); tasks.remove().run(); }
        int size() { return tasks.size(); }
    }
    private static final class Fake implements PasswordChangeViewModel.Actions {
        boolean current=true,ownSignOut,loseAccountOnFailure; int changes,emails; String[] arguments; Exception failure;
        public void requireCurrent() { if(!current)throw new IllegalStateException("STALE_ACCOUNT"); }
        public boolean completedSignOut() { return ownSignOut; }
        public void change(String currentPassword,String password,String confirmation,String code) throws Exception {
            ++changes; arguments=new String[]{currentPassword,password,confirmation,code};
            if(failure!=null) { if(loseAccountOnFailure)current=false; throw failure; }
            current=false; ownSignOut=true;
        }
        public void requestCode() throws Exception { ++emails; if(failure!=null)throw failure; }
    }
}
