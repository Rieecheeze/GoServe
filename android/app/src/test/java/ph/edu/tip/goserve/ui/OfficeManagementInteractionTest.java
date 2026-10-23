package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.lifecycle.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.data.AccountIntegrationTest;
import ph.edu.tip.goserve.data.OfficeManagementRepository.*;
import ph.edu.tip.goserve.domain.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=GoServeApplication.class,qualifiers="w390dp-h844dp-port-notnight-mdpi",shadows={AccountIntegrationTest.Store.class,AccountIntegrationTest.Server.class},instrumentedPackages={"ph.edu.tip.goserve.data"})
@LooperMode(LooperMode.Mode.PAUSED) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class OfficeManagementInteractionTest {
    final ThreadInteractionTest host=new ThreadInteractionTest();final OfficeManagementViewModelTest.Q work=new OfficeManagementViewModelTest.Q(),ui=new OfficeManagementViewModelTest.Q();
    A actions;OfficeManagementViewModel model;OfficeManagementFragment fragment;
    @Before public void setup()throws Exception{host.setup();actions=new A();actions.actor=InquiryViewModelTest.ACCOUNT;}
    @After public void close(){host.close();}
    @Test public void ownStatusAndNoteAreLoadedWithoutAnyWrite(){open(Kind.OFFICE);assertEquals("Actual note",text(R.id.officeMgmtText));assertEquals("Open",text(R.id.officeMgmtStatus));assertTrue(text(R.id.officeMgmtScope).contains("Registrar"));assertTrue(actions.sent.isEmpty());}
    @Test public void statusSelectionThenReviewNeedsAnotherExplicitSaveClick(){open(Kind.OFFICE);v(R.id.officeMgmtStatus).performClick();androidx.appcompat.app.AlertDialog d=(androidx.appcompat.app.AlertDialog)ShadowDialog.getLatestDialog();assertNotNull(d);d.getListView().performItemClick(null,2,2);((EditText)v(R.id.officeMgmtText)).setText("Entered office note");v(R.id.officeMgmtPrimary).performClick();assertEquals(OfficeManagementViewModel.Mode.REVIEW,model.state().getValue().mode);assertTrue(text(R.id.officeMgmtSummary).contains("Cut-Off Reached"));assertTrue(text(R.id.officeMgmtSummary).contains("Entered office note"));assertTrue(actions.sent.isEmpty());v(R.id.officeMgmtPrimary).performClick();flush();assertEquals(1,actions.sent.size());assertEquals(View.VISIBLE,v(R.id.officeMgmtSaved).getVisibility());}
    @Test public void unknownSaveProtectsTheSameCommandAndGlobalNavigation(){open(Kind.OFFICE);v(R.id.officeMgmtPrimary).performClick();actions.failure=new java.io.IOException();v(R.id.officeMgmtPrimary).performClick();flush();Command command=model.state().getValue().command;assertTrue(fragment.mutationPending());assertFalse(v(R.id.officeMgmtBack).isEnabled());assertTrue(text(R.id.officeMgmtError).contains("unconfirmed"));CapsuleNavigationView nav=host.activity.findViewById(R.id.bottomNavigation);nav.setSelectedItemId(R.id.nav_more);host.activity.getSupportFragmentManager().executePendingTransactions();assertSame(fragment,host.activity.getSupportFragmentManager().findFragmentById(R.id.mainContainer));actions.failure=null;v(R.id.officeMgmtPrimary).performClick();flush();assertSame(command,actions.sent.get(1));}
    @Test public void returningToEditDoesNotSaveOrLoseEnteredAnnouncement(){open(Kind.ANNOUNCEMENT);v(R.id.officeMgmtCreate).performClick();((EditText)v(R.id.officeMgmtText)).setText("Office update exactly.");v(R.id.officeMgmtPrimary).performClick();assertEquals(OfficeManagementViewModel.Mode.REVIEW,model.state().getValue().mode);v(R.id.officeMgmtEdit).performClick();assertEquals("Office update exactly.",text(R.id.officeMgmtText));assertTrue(actions.sent.isEmpty());}
    @Test public void newAnnouncementStartsHiddenAndOnlyPublishesAfterReview(){open(Kind.ANNOUNCEMENT);v(R.id.officeMgmtCreate).performClick();assertFalse(((CheckBox)v(R.id.officeMgmtActive)).isChecked());((EditText)v(R.id.officeMgmtText)).setText("Actual office announcement");((CheckBox)v(R.id.officeMgmtActive)).setChecked(true);v(R.id.officeMgmtPrimary).performClick();assertTrue(actions.sent.isEmpty());v(R.id.officeMgmtPrimary).performClick();flush();assertEquals(1,actions.sent.size());assertEquals("Actual office announcement",actions.form.text);assertTrue(actions.form.active);}
    @Test public void requiredServiceFeeUsesEnteredAmountCurrencyAndDelivery(){open(Kind.SERVICE);v(R.id.officeMgmtCreate).performClick();assertEquals("",text(R.id.officeMgmtName));assertFalse(((CheckBox)v(R.id.officeMgmtActive)).isChecked());((EditText)v(R.id.officeMgmtName)).setText("Entered demo service");((CheckBox)v(R.id.officeMgmtPhysical)).setChecked(true);v(R.id.officeMgmtFeeRequired).performClick();assertEquals(View.VISIBLE,v(R.id.officeMgmtMoney).getVisibility());((EditText)v(R.id.officeMgmtAmount)).setText("27.50");((EditText)v(R.id.officeMgmtCurrency)).setText("ABC");v(R.id.officeMgmtPrimary).performClick();assertEquals(OfficeManagementViewModel.Mode.REVIEW,model.state().getValue().mode);assertTrue(text(R.id.officeMgmtSummary).contains("27.50 ABC"));assertEquals("27.50",actions.form.amount);assertEquals("ABC",actions.form.currency);assertTrue(actions.form.physical);assertTrue(actions.sent.isEmpty());}
    @Test public void invalidRequiredFeeAndMissingNameHaveAdjacentErrors(){open(Kind.SERVICE);v(R.id.officeMgmtCreate).performClick();v(R.id.officeMgmtFeeRequired).performClick();v(R.id.officeMgmtPrimary).performClick();assertNotNull(((EditText)v(R.id.officeMgmtName)).getError());assertNotNull(((EditText)v(R.id.officeMgmtAmount)).getError());assertNotNull(((EditText)v(R.id.officeMgmtCurrency)).getError());assertEquals(OfficeManagementViewModel.Mode.EDIT,model.state().getValue().mode);assertTrue(actions.sent.isEmpty());}
    @Test public void viewRecreationRetainsAnUnsentEditWithoutAnotherWrite(){open(Kind.OFFICE);((EditText)v(R.id.officeMgmtText)).setText("Keep this edit");host.activity.getSupportFragmentManager().beginTransaction().detach(fragment).commitNow();host.activity.getSupportFragmentManager().beginTransaction().attach(fragment).commitNow();ThreadInteractionTest.idle();assertEquals("Keep this edit",text(R.id.officeMgmtText));assertTrue(actions.sent.isEmpty());}
    @Test public void accountReplacementRemovesEnteredInformationAndRows(){open(Kind.OFFICE);((EditText)v(R.id.officeMgmtText)).setText("Private edit");actions.current=false;((MutableLiveData<Long>)((GoServeApplication)host.activity.getApplication()).accountInvalidations()).setValue(901L);ThreadInteractionTest.idle();assertEquals("",text(R.id.officeMgmtText));assertEquals("",text(R.id.officeMgmtScope));assertFalse(v(R.id.officeMgmtPrimary).isEnabled());}
    @Test public void captureReviewInBothThemesAndReachTheFinalSaveAtLargeText()throws Exception{for(boolean dark:new boolean[]{false,true}){RuntimeEnvironment.setQualifiers("w390dp-h844dp-port-"+(dark?"night":"notnight")+"-mdpi");open(Kind.OFFICE);v(R.id.officeMgmtPrimary).performClick();assertEquals(OfficeManagementViewModel.Mode.REVIEW,model.state().getValue().mode);host.capture("office-management-"+(dark?"dark":"light")+"-review.png",390,844);reset();RuntimeEnvironment.setQualifiers("w320dp-h480dp-port-"+(dark?"night":"notnight")+"-mdpi");RuntimeEnvironment.setFontScale(2f);open(Kind.OFFICE);v(R.id.officeMgmtPrimary).performClick();host.measure(320,480);ScrollView scroll=(ScrollView)v(R.id.officeMgmtScroll);scroll.scrollTo(0,scroll.getChildAt(0).getHeight());ThreadInteractionTest.idle();host.capture("office-management-large-"+(dark?"dark":"light")+"-end.png",320,480);android.graphics.Rect rect=new android.graphics.Rect();assertTrue(v(R.id.officeMgmtPrimary).getGlobalVisibleRect(rect));assertEquals(v(R.id.officeMgmtPrimary).getHeight(),rect.height());reset();RuntimeEnvironment.setFontScale(1f);}}
    void open(Kind kind){host.open(true);model=new OfficeManagementViewModel(RuntimeEnvironment.getApplication(),actions,work,ui,new SavedStateHandle());ViewModelProvider.Factory factory=new ViewModelProvider.Factory(){@Override public <T extends ViewModel>T create(Class<T> type){return type.cast(model);}};fragment=new OfficeManagementFragment(factory);Bundle args=new Bundle();args.putString("kind",kind.name());fragment.setArguments(args);host.activity.getSupportFragmentManager().beginTransaction().replace(R.id.mainContainer,fragment).commitNow();flush();}
    @Test public void captureEnteredServiceAndAnnouncementFormsWithoutPublishing()throws Exception{
        for(boolean dark:new boolean[]{false,true}){
            RuntimeEnvironment.setQualifiers("w390dp-h844dp-port-"+(dark?"night":"notnight")+"-mdpi");open(Kind.SERVICE);v(R.id.officeMgmtCreate).performClick();
            ((EditText)v(R.id.officeMgmtName)).setText("Entered demo service");((CheckBox)v(R.id.officeMgmtDigital)).setChecked(true);v(R.id.officeMgmtFeeRequired).performClick();
            ((EditText)v(R.id.officeMgmtRequirements)).setText("Entered supporting document");((EditText)v(R.id.officeMgmtAmount)).setText("27.50");((EditText)v(R.id.officeMgmtCurrency)).setText("ABC");
            host.capture("office-service-"+(dark?"dark":"light")+"-form.png",390,844);ScrollView scroll=(ScrollView)v(R.id.officeMgmtScroll);scroll.scrollTo(0,scroll.getChildAt(0).getHeight());ThreadInteractionTest.idle();host.capture("office-service-"+(dark?"dark":"light")+"-end.png",390,844);assertTrue(actions.sent.isEmpty());reset();
            open(Kind.ANNOUNCEMENT);v(R.id.officeMgmtCreate).performClick();((EditText)v(R.id.officeMgmtText)).setText("Entered demo office announcement");host.capture("office-announcement-"+(dark?"dark":"light")+"-form.png",390,844);assertFalse(((CheckBox)v(R.id.officeMgmtActive)).isChecked());assertTrue(actions.sent.isEmpty());reset();
        }
    }
    void flush(){for(int n=0;n<30;n++){ThreadInteractionTest.idle();if(work.jobs.isEmpty() && ui.jobs.isEmpty())return;while(!work.jobs.isEmpty())work.run();while(!ui.jobs.isEmpty())ui.run();}fail("Unbounded UI work");}
    View v(int id){return host.activity.findViewById(id);}String text(int id){return ((TextView)v(id)).getText().toString();}
    void reset(){host.reset();work.jobs.clear();ui.jobs.clear();actions=new A();actions.actor=InquiryViewModelTest.ACCOUNT;}
    static final class A extends OfficeManagementViewModelTest.A {
        OfficeManagementViewModel.State form;Announcement published;
        @Override public Command draft(OfficeManagementViewModel.State s,Command previous)throws Exception{form=s;return super.draft(s,previous);}
        @Override public Page<Announcement> announcements(String time,String id){loads++;return new Page<>(published==null?List.of():List.of(published),null,null);}
        @Override public Result save(Command c)throws Exception{if(c.kind==Kind.OFFICE)return super.save(c);sent.add(c);if(failure!=null)throw failure;if(c.kind==Kind.ANNOUNCEMENT){published=new Announcement(OfficeManagementViewModelTest.ID,"OFF-REG",form.text,actor,"2026-10-22T00:00:00Z",form.active,1);return new Result(null,published,null);}return new Result(null,null,new Catalog(new RequestData.Service(OfficeManagementViewModelTest.ID,"OFF-REG",form.name,1,form.fee,form.fee==FeeDeclaration.REQUIRED?new BigDecimal(form.amount):form.fee==FeeDeclaration.FREE?BigDecimal.ZERO:null,form.fee==FeeDeclaration.REQUIRED?form.currency:null,List.of(form.physical?"physical":"digital"),List.of(),form.active),true,"[]"));}
    }
}
