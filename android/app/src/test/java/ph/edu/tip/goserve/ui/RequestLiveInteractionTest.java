package ph.edu.tip.goserve.ui;

import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.data.AccountIntegrationTest;
import ph.edu.tip.goserve.data.RealtimeDataSource;
import ph.edu.tip.goserve.data.RequestRepository;
import ph.edu.tip.goserve.domain.FeeDeclaration;
import ph.edu.tip.goserve.domain.PaymentData;
import ph.edu.tip.goserve.domain.PaymentMethod;
import ph.edu.tip.goserve.domain.PaymentStatus;
import ph.edu.tip.goserve.domain.RequestData;
import ph.edu.tip.goserve.domain.RequestStatus;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=GoServeApplication.class,qualifiers="w390dp-h844dp-port-notnight-mdpi",
    shadows={AccountIntegrationTest.Store.class,AccountIntegrationTest.Server.class,ph.edu.tip.goserve.data.AndroidFileProviderPaths.class},instrumentedPackages={"ph.edu.tip.goserve.data","androidx.core.content"})
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class RequestLiveInteractionTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private int viewportWidth=390,viewportHeight=844;
    private final Queue work=new Queue(),ui=new Queue();
    private final ListActions listActions=new ListActions();
    private final DetailActions detailActions=new DetailActions();
    private final Session session=new Session();
    private final CatalogueActions catalogueActions=new CatalogueActions();
    private final ComposeActions composeActions=new ComposeActions();
    private final EvidenceActions evidenceActions=new EvidenceActions();
    private final ActionActions actionActions=new ActionActions();
    private final PdfActions pdfActions=new PdfActions();
    @Before public void reset() throws Exception {
        ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory.class,"_instance",null);
        ph.edu.tip.goserve.data.AndroidFileProviderPaths.reset();
        Shadows.shadowOf(android.webkit.MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping("jpg","image/jpeg");
        new AccountIntegrationTest().setup();
        ReflectionHelpers.setStaticField(AccountIntegrationTest.Server.class,"profileId",id(90));
        ReflectionHelpers.setStaticField(AccountIntegrationTest.Server.class,"email","fixture@demo.goserve.test");
        ReflectionHelpers.setStaticField(AccountIntegrationTest.Server.class,"recoveryAmr",false);
        GoServeApplication app=(GoServeApplication)RuntimeEnvironment.getApplication();
        assertTrue(app.auth().signInPassword("fixture@demo.goserve.test","fixture-password").ready());
        ReflectionHelpers.setField(app,"realtimeSession",session);
    }
    @After public void close() {
        if(controller!=null)controller.close(); RuntimeEnvironment.setFontScale(1f);
        ReflectionHelpers.setField(RuntimeEnvironment.getApplication(),"realtimeSession",null);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
    @Test public void productionLoadingHasNoPreviewRowsAndOnlySuccessfulReadCanShowEmpty() {
        listActions.page=new RequestData.RequestPage(List.of(),null,null); launchList();
        assertFalse(activity.isPreview()); assertEquals(1,work.size());
        assertEquals(View.VISIBLE,view(R.id.liveRequestLoading).getVisibility());
        assertEquals(View.GONE,view(R.id.liveRequestEmpty).getVisibility()); assertEquals(0,listActions.reads);
        complete(); assertEquals(View.VISIBLE,view(R.id.liveRequestEmpty).getVisibility());
        assertEquals(View.GONE,view(R.id.liveRequestError).getVisibility());
        assertEquals(View.VISIBLE,view(R.id.liveRequestBrowse).getVisibility());
    }
    @Test public void listShowsCanonicalReferenceStatusAndPendingFeeWithoutInventedAmount() {
        launchList(); complete(); assertEquals("Configured service",text(R.id.liveRequestName));
        assertEquals("OFF-REG",text(R.id.liveRequestOffice)); assertTrue(text(R.id.liveRequestReference).contains("canonical-1"));
        assertEquals("Submitted",text(R.id.liveRequestStatus)); assertTrue(text(R.id.liveRequestFee).contains("Awaiting office"));
        assertFalse(text(R.id.liveRequestSection).contains("examples"));
    }
    @Test public void listRetryDoesNotExposeTransportDetailsOrMislabelFailureAsEmpty() {
        listActions.error=new java.io.IOException("private database host"); launchList(); complete();
        assertEquals(View.VISIBLE,view(R.id.liveRequestError).getVisibility());
        assertFalse(text(R.id.liveRequestError).contains("private")); assertEquals(View.GONE,view(R.id.liveRequestEmpty).getVisibility());
        listActions.error=null; view(R.id.liveRequestRetry).performClick(); complete(); assertEquals(2,listActions.reads);
        assertEquals("Configured service",text(R.id.liveRequestName));
    }
    @Test public void paginationUsesBothCanonicalCursorFieldsAndAppendsOnlyReturnedRows() {
        listActions.page=new RequestData.RequestPage(List.of(record(1,FeeDeclaration.PENDING_REVIEW)),"2026-10-21T01:00:00Z",id(1));
        launchList(); complete(); assertEquals(View.VISIBLE,view(R.id.liveRequestMore).getVisibility());
        listActions.page=new RequestData.RequestPage(List.of(record(2,FeeDeclaration.FREE)),null,null);
        view(R.id.liveRequestMore).performClick(); complete(); assertEquals(id(1),listActions.cursorId);
        assertEquals("2026-10-21T01:00:00Z",listActions.cursorAt);
        assertEquals(4,((RecyclerView)view(R.id.liveRequestList)).getAdapter().getItemCount());
        assertEquals(View.GONE,view(R.id.liveRequestMore).getVisibility());
    }
    @Test public void canonicalRowOpensItsRequestRouteAndBackReturnsToList() {
        launchList(); complete(); ((View)view(R.id.liveRequestName).getParent()).performClick(); idle();
        androidx.fragment.app.Fragment detail=activity.getSupportFragmentManager().findFragmentById(R.id.mainContainer);
        assertTrue(detail instanceof RequestDetailFragment); assertEquals(id(1),detail.requireArguments().getString("request_id"));
        activity.getOnBackPressedDispatcher().onBackPressed(); idle(); assertTrue(activity.getSupportFragmentManager().findFragmentById(R.id.mainContainer) instanceof RequestListFragment);
    }
    @Test public void listAccountLossClearsRowsAndDisablesRefreshAndPagination() {
        launchList(); complete(); listActions.current=false; invalidateAccount();
        assertEquals(2,((RecyclerView)view(R.id.liveRequestList)).getAdapter().getItemCount());
        assertFalse(view(R.id.liveRequestRefresh).isEnabled()); assertEquals(View.VISIBLE,view(R.id.liveRequestError).getVisibility());
        assertEquals(View.GONE,view(R.id.liveRequestEmpty).getVisibility()); assertEquals(View.GONE,view(R.id.liveRequestMore).getVisibility());
    }
    @Test public void listInvalidationsRefreshCanonicalReadsAndSubscriptionCloses() {
        launchList(); complete(); assertEquals(Set.of("requests"),session.tables); session.invalidate(); complete(); assertEquals(2,listActions.reads);
        int closed=session.closed;activity.navigate(new MoreFragment(),false); idle(); assertEquals(closed+1,session.closed);
    }
    @Test public void officeQueueFiltersIssueServerReadsAndClearThePriorCategory() {
        listActions.office="OFF-REG"; launchList(); complete();
        assertEquals("Request Queue",text(R.id.liveRequestTitle)); assertEquals(View.VISIBLE,view(R.id.queueFilters).getVisibility());
        assertTrue(view(R.id.queueAll).isSelected()); assertTrue(text(R.id.liveRequestSection).contains("OFF-REG"));
        listActions.page=new RequestData.RequestPage(List.of(),null,null); view(R.id.queueProcessing).performClick(); idle();
        assertTrue(view(R.id.queueProcessing).isSelected()); assertEquals(2,((RecyclerView)view(R.id.liveRequestList)).getAdapter().getItemCount());
        complete(); assertEquals(RequestStatus.PROCESSING,listActions.filter); assertEquals("No Requests With This Status",text(R.id.liveRequestEmptyTitle));
        assertNull(listActions.cursorAt); assertNull(listActions.cursorId);
    }
    @Test public void studentHistoryDoesNotExposeOfficeQueueFilters() {
        launchList(); complete(); assertEquals(View.GONE,view(R.id.queueFilters).getVisibility());
        view(R.id.queueProcessing).performClick(); assertEquals(0,work.size()); assertEquals(1,listActions.reads);
    }
    @Test public void officeQueueAccountLossHidesFiltersAndClearsTheSelectedCategory() {
        listActions.office="DEP-CCS"; listActions.page=new RequestData.RequestPage(List.of(),null,null); launchList(); complete();
        assertEquals(View.VISIBLE,view(R.id.queueFilters).getVisibility());
        listActions.current=false; invalidateAccount(); assertEquals(View.GONE,view(R.id.queueFilters).getVisibility());
        assertEquals(2,((RecyclerView)view(R.id.liveRequestList)).getAdapter().getItemCount());
    }
    @Test public void captureAssignedOfficeQueueAndFilteredEmptyInBothThemes() throws Exception {
        for(boolean dark:new boolean[]{false,true}) {
            theme(dark); listActions.office="OFF-REG"; listActions.page=new RequestData.RequestPage(List.of(record(1,FeeDeclaration.FREE)),null,null);
            launchList(); complete(); capture("office-queue-"+(dark?"dark":"light")+".png",390,844);
            listActions.page=new RequestData.RequestPage(List.of(),null,null); view(R.id.queueReady).performClick(); complete();
            capture("office-queue-empty-"+(dark?"dark":"light")+".png",390,844);
            controller.close(); controller=null; reset();
        }
    }
    @Test @Config(shadows={AccountIntegrationTest.Store.class,AccountIntegrationTest.Server.class,SeryoInteractionTest.Api26FontScaling.class})
    public void smallLargeTextOfficeFiltersRemainReachableByHorizontalScroll() throws Exception {
        theme(true); RuntimeEnvironment.setFontScale(2f); RuntimeEnvironment.setQualifiers("w320dp-h480dp-port-night-mdpi");
        viewportWidth=320; viewportHeight=480; listActions.office="OFF-REG"; launchList(); complete(); layout(320,480);
        android.widget.HorizontalScrollView filters=(android.widget.HorizontalScrollView)view(R.id.queueFilters);
        filters.fullScroll(View.FOCUS_RIGHT); idle(); layout(320,480);
        View ready=view(R.id.queueReady); assertTrue(ready.getHeight()>=48);
        ready.requestRectangleOnScreen(new android.graphics.Rect(0,0,ready.getWidth(),ready.getHeight()),true); idle(); layout(320,480);
        assertFullyVisible(ready); android.graphics.Rect bounds=new android.graphics.Rect(); assertTrue(ready.getGlobalVisibleRect(bounds)); assertTrue(bounds.width()>=ready.getWidth());
        listActions.page=new RequestData.RequestPage(List.of(),null,null); ready.performClick(); complete();
        assertEquals(RequestStatus.READY_FOR_PICKUP_DOWNLOAD,listActions.filter); capture("office-queue-small-dark.png",320,480);
        RecyclerView list=(RecyclerView)view(R.id.liveRequestList); list.scrollBy(0,10000); idle(); layout(320,480);
        assertFullyVisible(view(R.id.liveRequestEmptyBody)); capture("office-queue-small-dark-end.png",320,480);
    }
    @Test public void detailUsesActualPaymentWithoutAdvancingServerRequestProgress() {
        detailActions.payment=paid(); launchDetail(); assertEquals(View.VISIBLE,view(R.id.requestDetailLoading).getVisibility());
        assertEquals(View.GONE,view(R.id.requestDetailContent).getVisibility()); complete();
        assertEquals(id(1),detailActions.requestedId); assertEquals(id(1),detailActions.paymentRequest);
        assertEquals("Awaiting Payment",text(R.id.requestDetailStatus));
        assertEquals("Payment confirmed",text(R.id.requestPaymentStatus)); assertTrue(text(R.id.requestFeeDeclaration).contains("35.00"));
        assertEquals(5,((android.view.ViewGroup)view(R.id.requestPhases)).getChildCount());
        assertEquals(View.GONE,view(R.id.requestFileAction).getVisibility());
    }
    @Test public void failedPaymentReadCannotShowConfirmedAndCanBeRetried() {
        detailActions.paymentError=new java.io.IOException("private transport"); launchDetail(); complete();
        assertTrue(text(R.id.requestPaymentStatus).contains("could not be checked")); assertFalse(text(R.id.requestPaymentStatus).contains("confirmed"));
        assertEquals(View.VISIBLE,view(R.id.requestDetailRetry).getVisibility()); detailActions.paymentError=null; detailActions.payment=paid();
        view(R.id.requestDetailRetry).performClick(); complete(); assertEquals("Payment confirmed",text(R.id.requestPaymentStatus));
    }
    @Test public void missingRequestShowsSafeErrorWithoutPriorDetails() {
        launchDetail(); complete(); detailActions.error=new RequestRepository.WorkflowException("NOT_FOUND");
        view(R.id.requestDetailRefresh).performClick(); complete();
        assertEquals(View.GONE,view(R.id.requestDetailContent).getVisibility()); assertTrue(text(R.id.requestDetailNotice).contains("No accessible request"));
    }
    @Test public void detailAccountLossClearsPaymentRequestAndPhases() {
        detailActions.payment=paid(); launchDetail(); complete(); detailActions.current=false; invalidateAccount();
        assertEquals(View.GONE,view(R.id.requestDetailContent).getVisibility()); assertFalse(view(R.id.requestDetailRefresh).isEnabled());
        assertEquals("",text(R.id.requestDetailReference)); assertEquals("",text(R.id.requestPaymentStatus));
        assertEquals(0,((android.view.ViewGroup)view(R.id.requestPhases)).getChildCount());
    }
    @Test public void detailInvalidationsReadRequestsAndPaymentsWithoutIssuingWrites() {
        launchDetail(); complete(); assertEquals(Set.of("requests","payments"),session.tables);
        session.invalidate(); complete(); assertEquals(2,detailActions.reads);
        int closed=session.closed;activity.navigate(new MoreFragment(),false); idle(); assertEquals(closed+1,session.closed);
    }
    @Test public void captureCanonicalListAndEmptyStateInBothThemes() throws Exception {
        for(boolean dark:new boolean[]{false,true})for(boolean empty:new boolean[]{false,true}) {
            theme(dark); listActions.page=new RequestData.RequestPage(empty?List.of():List.of(record(1,FeeDeclaration.REQUIRED),record(2,FeeDeclaration.PENDING_REVIEW)),null,null);
            launchList(); complete(); layout(390,844);
            capture("request-list-"+(empty?"empty-":"records-")+(dark?"dark":"light")+".png",390,844);
            controller.close(); controller=null; reset();
        }
    }
    @Test public void captureCanonicalDetailsKeepsFeeAndPaymentStatesDistinct() throws Exception {
        for(boolean dark:new boolean[]{false,true})for(FeeDeclaration fee:FeeDeclaration.values()) {
            theme(dark); detailActions.result=record(1,fee); detailActions.payment=fee==FeeDeclaration.REQUIRED?paid():null;
            launchDetail(); complete(); layout(390,844);
            if(fee!=FeeDeclaration.REQUIRED)assertEquals(View.GONE,view(R.id.requestPaymentStatus).getVisibility());
            assertEquals(fee==FeeDeclaration.REQUIRED?5:4,((android.view.ViewGroup)view(R.id.requestPhases)).getChildCount());
            capture("request-detail-"+fee.wireValue+"-"+(dark?"dark":"light")+".png",390,844);
            controller.close(); controller=null; reset();
        }
    }
    @Test @Config(shadows={AccountIntegrationTest.Store.class,AccountIntegrationTest.Server.class,SeryoInteractionTest.Api26FontScaling.class})
    public void smallLargeTextScreensCanScrollToEveryRequestControl() throws Exception {
        theme(true); RuntimeEnvironment.setFontScale(2f); RuntimeEnvironment.setQualifiers("w320dp-h480dp-port-night-mdpi");
        viewportWidth=320; viewportHeight=480;
        listActions.page=new RequestData.RequestPage(List.of(),null,null); launchList(); complete(); layout(320,480);
        RecyclerView list=(RecyclerView)view(R.id.liveRequestList); assertTrue(list.canScrollVertically(1));
        list.scrollToPosition(0); idle(); layout(320,480);
        capture("request-empty-small-dark.png",320,480);
        list.scrollBy(0,10000); idle(); layout(320,480);
        android.graphics.Rect browse=new android.graphics.Rect(); assertTrue(view(R.id.liveRequestBrowse).getGlobalVisibleRect(browse));
        assertTrue(browse.height()>=view(R.id.liveRequestBrowse).getHeight());
        assertTrue(view(R.id.bottomNavigation).getBottom()<=480);
        capture("request-empty-small-dark-end.png",320,480); controller.close(); controller=null; reset();
        detailActions.result=record(1,FeeDeclaration.REQUIRED); detailActions.payment=paid(); launchDetail(); complete(); layout(320,480);
        androidx.core.widget.NestedScrollView scroll=(androidx.core.widget.NestedScrollView)view(R.id.requestDetailScroll);
        assertTrue(scroll.canScrollVertically(1)); assertTrue(view(R.id.requestDetailBack).getHeight()>=48);
        capture("request-detail-small-dark.png",320,480);
        scroll.scrollTo(0,scroll.getChildAt(0).getHeight()); idle(); layout(320,480);
        assertTrue(text(R.id.requestPaymentStatus).contains("confirmed"));
        android.graphics.Rect bounds=new android.graphics.Rect(); assertTrue(view(R.id.requestPaymentNote).getGlobalVisibleRect(bounds));
        assertTrue(bounds.height()>=view(R.id.requestPaymentNote).getHeight());
        capture("request-detail-small-dark-end.png",320,480);
    }
    private void theme(boolean dark) {
        RuntimeEnvironment.setQualifiers("w390dp-h844dp-port-"+(dark?"night":"notnight")+"-mdpi");
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(dark?androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES:androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
    }
    @Test public void composeCatalogueHasNoExamplesAndOnlySuccessfulReadsCanShowEmpty() {
        catalogueActions.page=new RequestData.ServicePage(List.of(),null); launchCompose();
        assertEquals(View.VISIBLE,view(R.id.composeLoading).getVisibility()); assertFalse(view(R.id.composeService).isEnabled());
        complete(); assertTrue(text(R.id.composeNotice).contains("no active services")); assertFalse(view(R.id.composePrimary).isEnabled());
        assertFalse(text(R.id.composeFee).contains("Free"));
    }
    @Test public void composeSelectionRequiresReviewBeforeTheOnlySubmissionWrite() {
        launchCompose(); complete(); chooseService(); assertEquals(1,work.size()); assertFalse(view(R.id.composePrimary).isEnabled());
        complete(); assertTrue(text(R.id.composeFee).contains("Free"));
        ((android.widget.EditText)view(R.id.composeNotes)).setText("Student supplied purpose");
        view(R.id.composePrimary).performClick(); idle(); assertEquals(0,work.size()); assertEquals(0,composeActions.submits);
        assertEquals("Submit request",text(R.id.composePrimary)); assertEquals(View.VISIBLE,view(R.id.composeReviewNotice).getVisibility());
        view(R.id.composePrimary).performClick(); assertEquals(1,work.size()); assertFalse(view(R.id.composePrimary).isEnabled()); complete();
        assertEquals(1,composeActions.submits); assertEquals("Student supplied purpose",composeActions.submitted.notes);
        assertEquals("View request",text(R.id.composePrimary)); assertTrue(text(R.id.composeNotice).contains("canonical-created"));
    }
    @Test public void composeCashIsNeverAutomaticallySelectedForARequiredFee() {
        composeActions.quote=formQuote(FeeDeclaration.REQUIRED); launchCompose(); complete(); chooseService(); complete();
        assertTrue(text(R.id.composeFee).contains("35.00"));
        com.google.android.material.checkbox.MaterialCheckBox cash=(com.google.android.material.checkbox.MaterialCheckBox)view(R.id.composeCash);
        assertFalse(cash.isChecked()); view(R.id.composePrimary).performClick(); assertEquals(0,composeActions.submits);
        assertEquals("Review request",text(R.id.composePrimary)); cash.performClick(); view(R.id.composePrimary).performClick();
        assertEquals("Submit request",text(R.id.composePrimary));
    }
    @Test public void composeTestCheckoutIsCapabilityGatedAndMutuallyExclusiveWithCash() {
        composeActions.quote=formQuote(FeeDeclaration.REQUIRED); composeActions.egovpay=true;
        launchCompose(); complete(); chooseService(); complete();
        com.google.android.material.checkbox.MaterialCheckBox cash=(com.google.android.material.checkbox.MaterialCheckBox)view(R.id.composeCash);
        com.google.android.material.checkbox.MaterialCheckBox egov=(com.google.android.material.checkbox.MaterialCheckBox)view(R.id.composeEgovpay);
        assertTrue(egov.isEnabled()); assertFalse(egov.isChecked()); assertFalse(cash.isChecked());
        cash.performClick(); assertTrue(cash.isChecked()); egov.performClick(); assertTrue(egov.isChecked()); assertFalse(cash.isChecked());
        cash.performClick(); assertTrue(cash.isChecked()); assertFalse(egov.isChecked());
    }
    @Test public void composeUnavailableTestCheckoutDoesNotAffectCashSelection() {
        composeActions.quote=formQuote(FeeDeclaration.REQUIRED); launchCompose(); complete(); chooseService(); complete();
        assertFalse(view(R.id.composeEgovpay).isEnabled()); assertTrue(text(R.id.composeEgovpayNotice).contains("unavailable"));
        view(R.id.composeCash).performClick(); view(R.id.composePrimary).performClick(); assertEquals("Submit request",text(R.id.composePrimary));
    }
    @Test public void requestCheckoutEntryRequiresOwnedPendingEgovPaymentAndEligibleRequest() {
        detailActions.payment=new PaymentData.Record(id(90),id(9),id(1),PaymentMethod.EGOVPAY,PaymentStatus.PENDING,new BigDecimal("35.00"),"PHP",1,null,null);
        launchDetail(); complete(); assertEquals(View.VISIBLE,view(R.id.requestEgovpayOpen).getVisibility());
        detailActions.payment=paid(); view(R.id.requestDetailRefresh).performClick(); complete(); assertEquals(View.GONE,view(R.id.requestEgovpayOpen).getVisibility());
        detailActions.payment=new PaymentData.Record(id(90),id(9),id(1),PaymentMethod.EGOVPAY,PaymentStatus.PENDING,new BigDecimal("35.00"),"PHP",1,null,null);
        RequestData.Record held=detailActions.result;
        detailActions.result=new RequestData.Record(held.id,held.reference,id(89),held.officeId,held.deliveryMode,held.createdAt,held.version,held.status,held.snapshot);
        view(R.id.requestDetailRefresh).performClick(); complete(); assertEquals(View.GONE,view(R.id.requestEgovpayOpen).getVisibility());
    }
    @Test public void composeUncertainSubmissionFreezesTheIntentAndRetriesItExactly() {
        launchCompose(); complete(); chooseService(); complete(); view(R.id.composePrimary).performClick();
        composeActions.error=new java.io.IOException("private database host"); view(R.id.composePrimary).performClick(); complete();
        RequestData.Submission intent=composeActions.submitted; assertEquals("Retry submission",text(R.id.composePrimary));
        assertFalse(view(R.id.composeNotes).isEnabled()); assertEquals(View.GONE,view(R.id.composeEdit).getVisibility());
        assertFalse(text(R.id.composeNotice).contains("private")); composeActions.error=null; view(R.id.composePrimary).performClick(); complete();
        assertSame(intent,composeActions.submitted); assertEquals(2,composeActions.submits);
    }
    @Test public void composeCatalogueFailureRetriesWithoutShowingAnEmptySuccess() {
        catalogueActions.error=new java.io.IOException("private endpoint"); launchCompose(); complete();
        assertTrue(text(R.id.composeNotice).contains("could not be loaded")); assertFalse(text(R.id.composeNotice).contains("private"));
        catalogueActions.error=null; view(R.id.composeCatalogueRetry).performClick(); complete(); assertTrue(view(R.id.composeService).isEnabled());
    }
    @Test public void composeAccountLossClearsTheNotesAndQuoteAndPreventsFurtherSelection() {
        launchCompose(); complete(); chooseService(); complete(); ((android.widget.EditText)view(R.id.composeNotes)).setText("Private note");
        composeActions.current=false; catalogueActions.current=false; invalidateAccount();
        assertEquals("",text(R.id.composeNotes)); assertFalse(view(R.id.composeService).isEnabled()); assertFalse(text(R.id.composeFee).contains("Free"));
        assertTrue(text(R.id.composeNotice).contains("Sign in again"));
    }
    @Test public void composeBackEditsReviewThenReturnsToThePriorPageAndRestoresNavigation() {
        launchCompose(); complete(); chooseService(); complete(); assertEquals(View.GONE,view(R.id.bottomNavigation).getVisibility());
        view(R.id.composePrimary).performClick(); activity.getOnBackPressedDispatcher().onBackPressed(); idle();
        assertEquals("Review request",text(R.id.composePrimary));
        activity.getOnBackPressedDispatcher().onBackPressed(); idle();
        assertTrue(activity.getSupportFragmentManager().findFragmentById(R.id.mainContainer) instanceof HomeFragment);
        assertEquals(View.VISIBLE,view(R.id.bottomNavigation).getVisibility());
    }
    @Test public void officeEntryOpensOnlyItsCanonicalOfficeRequestForm() {
        launch(); activity.navigate(OfficeDetailFragment.create(new ph.edu.tip.goserve.domain.Office("OFF-REG","Registrar","Fixture campus",
            ph.edu.tip.goserve.domain.OfficeStatus.OPEN,"Fixture note","Documents",true)),true); idle(); layout(390,844);
        assertEquals(View.VISIBLE,view(R.id.startRequest).getVisibility()); view(R.id.startRequest).performClick(); idle();
        androidx.fragment.app.Fragment current=activity.getSupportFragmentManager().findFragmentById(R.id.mainContainer);
        assertTrue(current instanceof RequestComposeFragment); assertEquals("OFF-REG",current.requireArguments().getString("office_id"));
    }
    @Test public void cashierOfficeDoesNotOfferStudentDocumentSubmission() {
        launch(); activity.navigate(OfficeDetailFragment.create(new ph.edu.tip.goserve.domain.Office("OFF-CASH","Cashiering","Fixture campus",
            ph.edu.tip.goserve.domain.OfficeStatus.OPEN,"Fixture note","Payments",true)),true); idle();
        assertEquals(View.GONE,view(R.id.startRequest).getVisibility());
    }
    @Test public void evidencePickerRequestsExactlyTheConfiguredMimeTypesWithoutUploading() {
        openEvidence(); evidenceView(R.id.evidenceChoose).performClick();
        org.robolectric.shadows.ShadowActivity.IntentForResult picker=Shadows.shadowOf(activity).getNextStartedActivityForResult();
        assertNotNull(picker); assertEquals(Intent.ACTION_OPEN_DOCUMENT,picker.intent.getAction());
        assertArrayEquals(new String[]{"application/pdf"},picker.intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES));
        assertEquals(0,evidenceActions.prepares); assertEquals(0,evidenceActions.uploads);
        assertEquals(View.GONE,evidenceView(R.id.evidenceCamera).getVisibility());
    }
    @Test public void choosingEvidenceStaysLocalUntilExplicitUseAndOnlyThenAddsCanonicalMetadata() {
        openEvidence(); selectEvidence(); assertEquals(0,evidenceActions.prepares); assertTrue(evidenceView(R.id.evidenceUse).isEnabled());
        evidenceView(R.id.evidenceUse).performClick(); complete(); assertEquals(1,evidenceActions.prepares); assertEquals(0,evidenceActions.uploads);
        assertFalse(evidenceView(R.id.evidenceUse).isEnabled()); complete(); assertEquals(1,evidenceActions.uploads);
        RequestComposeViewModel model=new ViewModelProvider(currentComposer()).get(RequestComposeViewModel.class);
        assertEquals(id(5),model.state().getValue().evidence.get(0).id);
        assertNull(currentComposer().getChildFragmentManager().findFragmentByTag(RequestEvidenceDialog.TAG));
    }
    @Test public void evidenceUploadFailureHasNoFakeSuccessAndRetriesTheSamePreparedFile() {
        openEvidence(); selectEvidence(); evidenceActions.error=new java.io.IOException("private host");
        evidenceView(R.id.evidenceUse).performClick(); complete(); complete();
        assertEquals("Retry upload",((TextView)evidenceView(R.id.evidenceUse)).getText().toString());
        assertFalse(((TextView)evidenceView(R.id.evidenceErrorText)).getText().toString().contains("private host"));
        assertTrue(new ViewModelProvider(currentComposer()).get(RequestComposeViewModel.class).state().getValue().evidence.isEmpty());
        evidenceActions.error=null; evidenceView(R.id.evidenceUse).performClick(); complete();
        assertEquals(1,evidenceActions.prepares); assertEquals(2,evidenceActions.uploads);
    }
    @Test public void invalidEvidenceShowsTheActionableErrorAndAllowsAnotherSelection() {
        evidenceActions.readError=new RequestRepository.WorkflowException("UPLOAD_INVALID"); openEvidence(); selectEvidence();
        assertEquals(View.VISIBLE,evidenceView(R.id.evidenceError).getVisibility()); assertEquals(View.GONE,evidenceView(R.id.evidenceFile).getVisibility());
        assertFalse(evidenceView(R.id.evidenceUse).isEnabled()); assertTrue(evidenceView(R.id.evidenceChoose).isEnabled()); assertEquals(0,evidenceActions.prepares);
    }
    @Test public void evidenceAccountLossClearsThePrivateFilenameAndDisablesUse() {
        openEvidence(); selectEvidence(); evidenceActions.current=false; composeActions.current=false; catalogueActions.current=false; invalidateAccount();
        assertEquals("",((TextView)evidenceView(R.id.evidenceName)).getText().toString()); assertFalse(evidenceView(R.id.evidenceUse).isEnabled());
    }
    @Test public void cameraIntentUsesThePrivateProviderOnlyWhenJpegIsConfigured() {
        composeActions.uploadMime="image/jpeg"; openEvidence(); assertEquals(View.VISIBLE,evidenceView(R.id.evidenceCamera).getVisibility());
        evidenceView(R.id.evidenceCamera).performClick();
        org.robolectric.shadows.ShadowActivity.IntentForResult camera=Shadows.shadowOf(activity).getNextStartedActivityForResult();
        assertNotNull(camera); assertEquals(android.provider.MediaStore.ACTION_IMAGE_CAPTURE,camera.intent.getAction());
        android.net.Uri output=camera.intent.getParcelableExtra(android.provider.MediaStore.EXTRA_OUTPUT);
        assertNotNull(output); assertEquals("content",output.getScheme()); assertEquals(activity.getPackageName()+".evidence",output.getAuthority());
        assertTrue((camera.intent.getFlags()&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)!=0);
    }
    @Test public void cancelledCameraAfterActivityRecreationReleasesItsPrivateOutput() throws Exception {
        composeActions.uploadMime="image/jpeg"; openEvidence(); evidenceView(R.id.evidenceCamera).performClick();
        org.robolectric.shadows.ShadowActivity.IntentForResult camera=Shadows.shadowOf(activity).getNextStartedActivityForResult();
        assertNotNull(camera); android.net.Uri output=camera.intent.getParcelableExtra(android.provider.MediaStore.EXTRA_OUTPUT);
        controller.recreate(); activity=controller.get(); idle();
        activity.getActivityResultRegistry().dispatchResult(camera.requestCode,android.app.Activity.RESULT_CANCELED,null); idle();
        assertTrue(evidenceView(R.id.evidenceChoose).isEnabled()); assertEquals(0,evidenceActions.prepares);
        try { activity.getContentResolver().openInputStream(output); fail("Cancelled private camera file must be removed"); }
        catch(java.io.FileNotFoundException expected) {}
    }
    @Test public void reviewShowsTheCanonicalQuoteNameWhenCatalogueTextHasChanged() {
        RequestData.Service updated=new RequestData.Service(id(50),"OFF-REG","Updated configured service",2,FeeDeclaration.FREE,BigDecimal.ZERO,null,List.of("physical"),List.of(),true);
        composeActions.quote=new RequestData.Quote(id(70),id(90),"physical",updated,true);
        launchCompose(); complete(); chooseService(); complete();
        assertEquals("Updated configured service",text(R.id.composeService));
    }
    @Test public void staleQuoteOffersARealCatalogueReloadInsteadOfADeadRetryLoop() {
        composeActions.quoteError=new RequestRepository.WorkflowException("QUOTE_STALE"); launchCompose(); complete(); chooseService(); complete();
        assertEquals(View.VISIBLE,view(R.id.composeCatalogueRetry).getVisibility());
        view(R.id.composeCatalogueRetry).performClick(); complete(); assertEquals(2,catalogueActions.reads);
    }
    @Test public void serviceInvalidationRefreshesOpenChoicesWithoutOverwritingThePrivateDraft(){
        launchCompose();complete();chooseService();complete();((android.widget.EditText)view(R.id.composeNotes)).setText("Keep my unsent note");
        assertEquals(Set.of("services"),session.tables);int reads=catalogueActions.reads;
        session.invalidate();complete();assertEquals(reads+1,catalogueActions.reads);assertEquals("Keep my unsent note",text(R.id.composeNotes));
        RealtimeDataSource.Listener old=session.listener;controller.pause().stop();int stoppedReads=catalogueActions.reads;
        old.onStateChanged(RealtimeDataSource.State.CONNECTED);idle();assertEquals(0,work.size());assertEquals(stoppedReads,catalogueActions.reads);
    }
    @Test public void requestActionsRequireExplicitReviewAndFinalConfirmation() {
        openActions(true); assertEquals(0,actionActions.writes); actionChoice("Review: start processing").performClick(); idle();
        assertEquals(0,work.size()); assertTrue(actionView(R.id.actionConfirm).isEnabled());
        actionView(R.id.actionConfirm).performClick(); assertEquals(1,work.size()); assertFalse(actionView(R.id.actionConfirm).isEnabled());
        complete(); assertEquals(1,actionActions.writes); assertNull(currentDetail().getChildFragmentManager().findFragmentByTag(RequestActionDialog.TAG));
        assertEquals(1,work.size()); complete(); assertEquals(2,detailActions.reads);
    }
    @Test public void ownerCancellationNeedsConfiguredPolicyAndANonblankReason() {
        actionActions.cancel=true; openActions(false); assertEquals(1,((android.view.ViewGroup)actionView(R.id.actionChoices)).getChildCount());
        actionChoice("Review cancellation").performClick(); assertFalse(actionView(R.id.actionConfirm).isEnabled());
        ((android.widget.EditText)actionView(R.id.actionReason)).setText("No longer needed"); actionChoice("Review cancellation").performClick();
        assertTrue(actionView(R.id.actionConfirm).isEnabled()); assertEquals(0,actionActions.writes);
        actionView(R.id.actionConfirm).performClick(); complete(); assertEquals(RequestStatus.CANCELLED,actionActions.committed.target);
        assertEquals("No longer needed",actionActions.committed.reason);
    }
    @Test public void missingOwnerCancellationPolicyDoesNotOfferInventedActions() {
        openActions(false); assertEquals(0,((android.view.ViewGroup)actionView(R.id.actionChoices)).getChildCount());
        assertFalse(actionView(R.id.actionConfirm).isEnabled()); assertTrue(((TextView)actionView(R.id.actionNotice)).getText().toString().contains("No actions"));
        assertEquals(0,actionActions.writes);
    }
    @Test public void failedActionOptionsRemainRetryableAfterEditingTheReason() {
        actionActions.readError=new java.io.IOException("private host"); openActions(true);
        assertEquals(View.VISIBLE,actionView(R.id.actionRetryOptions).getVisibility());
        ((android.widget.EditText)actionView(R.id.actionReason)).setText("Office explanation");
        assertEquals(View.VISIBLE,actionView(R.id.actionRetryOptions).getVisibility());
        actionActions.readError=null; actionView(R.id.actionRetryOptions).performClick(); complete();
        assertNotNull(actionChoice("Review: start processing")); assertEquals(0,actionActions.writes);
    }
    @Test public void uncertainRequestActionFreezesTheReasonAndReplaysTheSameIntent() {
        openActions(true); actionChoice("Review: start processing").performClick(); actionActions.error=new java.io.IOException("private host");
        actionView(R.id.actionConfirm).performClick(); complete(); RequestData.Transition held=actionActions.committed;
        assertFalse(actionView(R.id.actionReason).isEnabled()); assertEquals(View.GONE,actionView(R.id.actionEdit).getVisibility());
        assertEquals("Retry same update",((TextView)actionView(R.id.actionConfirm)).getText().toString());
        assertFalse(((TextView)actionView(R.id.actionReview)).getText().toString().contains("No change has been sent"));
        assertFalse(((TextView)actionView(R.id.actionNotice)).getText().toString().contains("private host"));
        actionActions.error=null; actionView(R.id.actionConfirm).performClick(); complete(); assertSame(held,actionActions.committed);
    }
    @Test public void definitiveRequestVersionConflictOffersReloadWithoutAnotherWrite() {
        openActions(true); actionChoice("Review: start processing").performClick(); actionActions.error=new RequestRepository.WorkflowException("STALE_VERSION");
        actionView(R.id.actionConfirm).performClick(); complete(); assertEquals(View.VISIBLE,actionView(R.id.actionReload).getVisibility());
        assertFalse(actionView(R.id.actionConfirm).isEnabled()); actionView(R.id.actionReload).performClick(); idle();
        assertNull(currentDetail().getChildFragmentManager().findFragmentByTag(RequestActionDialog.TAG)); complete(); assertEquals(1,actionActions.writes);
    }
    @Test public void requestActionAccountLossClearsPrivateReasonAndDisablesConfirmation() {
        openActions(true); ((android.widget.EditText)actionView(R.id.actionReason)).setText("Private reason");
        actionActions.current=false; invalidateAccount(); assertEquals("",((TextView)actionView(R.id.actionReason)).getText().toString());
        assertFalse(actionView(R.id.actionConfirm).isEnabled()); assertEquals(0,actionActions.writes);
    }
    @Test public void captureOfficeActionEditReviewAndUncertainStatesInBothThemes() throws Exception {
        for(boolean dark:new boolean[]{false,true}) {
            theme(dark); actionActions.error=null; openActions(true);
            captureAction("request-action-edit-"+(dark?"dark":"light")+".png",390,844);
            actionChoice("Review: start processing").performClick(); idle();
            captureAction("request-action-review-"+(dark?"dark":"light")+".png",390,844);
            actionActions.error=new java.io.IOException("fixture response lost"); actionView(R.id.actionConfirm).performClick(); complete();
            captureAction("request-action-uncertain-"+(dark?"dark":"light")+".png",390,844);
            controller.close(); controller=null; reset();
        }
    }
    @Test @Config(shadows={AccountIntegrationTest.Store.class,AccountIntegrationTest.Server.class,SeryoInteractionTest.Api26FontScaling.class})
    public void smallLargeTextActionsCanReachTheChoiceEditAndPinnedConfirmation() throws Exception {
        theme(true); RuntimeEnvironment.setFontScale(2f); RuntimeEnvironment.setQualifiers("w320dp-h480dp-port-night-mdpi");
        viewportWidth=320; viewportHeight=480; openActions(true);
        captureAction("request-action-small-dark.png",320,480);
        View choice=actionChoice("Review: start processing");
        choice.requestRectangleOnScreen(new android.graphics.Rect(0,0,choice.getWidth(),choice.getHeight()),true); idle();
        assertFullyVisible(choice); choice.performClick(); idle(); captureAction("request-action-small-review-dark.png",320,480);
        assertFullyVisible(actionView(R.id.actionConfirm));
        androidx.core.widget.NestedScrollView scroll=(androidx.core.widget.NestedScrollView)actionView(R.id.actionScroll);
        assertTrue(scroll.canScrollVertically(1)); scroll.scrollTo(0,scroll.getChildAt(0).getHeight()); idle();
        assertFullyVisible(actionView(R.id.actionEdit)); captureAction("request-action-small-review-end-dark.png",320,480);
    }
    private void captureAction(String name,int width,int height) throws Exception {
        RequestActionDialog dialog=(RequestActionDialog)currentDetail().getChildFragmentManager().findFragmentByTag(RequestActionDialog.TAG);
        View root=dialog.requireView(); root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
        root.layout(0,0,width,height); idle(); captureView(root,name,width,height);
    }
    private void openActions(boolean staff) {
        actionActions.office=staff?"OFF-REG":null; detailActions.result=record(1,FeeDeclaration.FREE); launchDetail(); complete();
        view(R.id.requestActionOpen).performClick(); idle();
        assertNotNull(currentDetail().getChildFragmentManager().findFragmentByTag(RequestActionDialog.TAG)); complete();
    }
    private View actionView(int id) {
        RequestActionDialog dialog=(RequestActionDialog)currentDetail().getChildFragmentManager().findFragmentByTag(RequestActionDialog.TAG);
        assertNotNull(dialog); View value=dialog.requireDialog().findViewById(id); assertNotNull("Missing action control: "+id,value); return value;
    }
    private View actionChoice(String label) {
        android.view.ViewGroup choices=(android.view.ViewGroup)actionView(R.id.actionChoices);
        for(int i=0;i<choices.getChildCount();i++)if(((TextView)choices.getChildAt(i)).getText().toString().equals(label))return choices.getChildAt(i);
        throw new AssertionError("Missing configured action: "+label);
    }
    @Test public void captureConfiguredComposerAndEvidenceStatesInBothThemes() throws Exception {
        for(boolean dark:new boolean[]{false,true}) {
            theme(dark); composeActions.uploads=true; evidenceActions.readError=null;
            launchCompose(); complete(); chooseService(); complete(); layout(390,844);
            capture("request-compose-"+(dark?"dark":"light")+".png",390,844);
            view(R.id.composePrimary).performClick(); idle(); layout(390,844);
            capture("request-compose-review-"+(dark?"dark":"light")+".png",390,844);
            view(R.id.composeEdit).performClick(); view(R.id.composeAddEvidence).performClick(); idle(); selectEvidence();
            captureEvidence("request-evidence-selected-"+(dark?"dark":"light")+".png",390,844);
            evidenceActions.readError=new RequestRepository.WorkflowException("UPLOAD_INVALID"); selectEvidence();
            captureEvidence("request-evidence-error-"+(dark?"dark":"light")+".png",390,844);
            controller.close(); controller=null; reset();
        }
    }
    @Test @Config(shadows={AccountIntegrationTest.Store.class,AccountIntegrationTest.Server.class,SeryoInteractionTest.Api26FontScaling.class})
    public void smallLargeTextComposerAndEvidenceKeepActionsReachable() throws Exception {
        theme(true); RuntimeEnvironment.setFontScale(2f); RuntimeEnvironment.setQualifiers("w320dp-h480dp-port-night-mdpi");
        viewportWidth=320; viewportHeight=480; composeActions.uploads=true;
        launchCompose(); complete(); chooseService(); complete(); layout(320,480);
        androidx.core.widget.NestedScrollView form=(androidx.core.widget.NestedScrollView)view(R.id.composeScroll);
        assertTrue(form.canScrollVertically(1)); assertFullyVisible(view(R.id.composePrimary)); assertTrue(view(R.id.composeBack).getHeight()>=48);
        capture("request-compose-small-dark.png",320,480);
        form.scrollTo(0,form.getChildAt(0).getHeight()); idle(); layout(320,480);
        assertFullyVisible(view(R.id.composeAddEvidence)); capture("request-compose-small-dark-end.png",320,480);
        view(R.id.composeAddEvidence).performClick(); idle(); selectEvidence();
        captureEvidence("request-evidence-small-dark.png",320,480);
        androidx.core.widget.NestedScrollView files=(androidx.core.widget.NestedScrollView)evidenceView(R.id.evidenceScroll);
        assertTrue(files.canScrollVertically(1)); assertFullyVisible(evidenceView(R.id.evidenceUse));
        assertTrue(evidenceView(R.id.evidenceBack).getHeight()>=48);
        files.scrollTo(0,files.getChildAt(0).getHeight()); idle();
        assertFullyVisible(evidenceView(R.id.evidenceName)); captureEvidence("request-evidence-small-dark-end.png",320,480);
    }
    private static void assertFullyVisible(View view) {
        android.graphics.Rect bounds=new android.graphics.Rect(); assertTrue(view.getGlobalVisibleRect(bounds)); assertTrue(bounds.height()>=view.getHeight());
    }
    private void captureEvidence(String name,int width,int height) throws Exception {
        RequestEvidenceDialog dialog=(RequestEvidenceDialog)currentComposer().getChildFragmentManager().findFragmentByTag(RequestEvidenceDialog.TAG);
        View root=dialog.requireView(); root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
        root.layout(0,0,width,height); idle(); captureView(root,name,width,height);
    }
    private void openEvidence() {
        composeActions.uploads=true; launchCompose(); complete(); chooseService(); complete();
        view(R.id.composeAddEvidence).performClick(); idle();
        assertNotNull(currentComposer().getChildFragmentManager().findFragmentByTag(RequestEvidenceDialog.TAG));
    }
    private RequestComposeFragment currentComposer() { return (RequestComposeFragment)activity.getSupportFragmentManager().findFragmentById(R.id.mainContainer); }
    private View evidenceView(int id) {
        RequestEvidenceDialog dialog=(RequestEvidenceDialog)currentComposer().getChildFragmentManager().findFragmentByTag(RequestEvidenceDialog.TAG);
        assertNotNull(dialog); View value=dialog.requireDialog().findViewById(id); assertNotNull("Missing evidence control: "+id,value); return value;
    }
    private void selectEvidence() {
        evidenceView(R.id.evidenceChoose).performClick(); org.robolectric.shadows.ShadowActivity.IntentForResult picker=Shadows.shadowOf(activity).getNextStartedActivityForResult();
        assertNotNull(picker); activity.getActivityResultRegistry().dispatchResult(picker.requestCode,android.app.Activity.RESULT_OK,
            new Intent().setData(android.net.Uri.parse("content://fixture.evidence/selected.pdf"))); idle(); complete();
    }
    private void launchCompose() {
        launch(); GoServeApplication app=(GoServeApplication)activity.getApplication();
        ServiceCatalogueViewModel catalogue=new ServiceCatalogueViewModel(app,catalogueActions,work,ui);
        RequestComposeViewModel compose=new RequestComposeViewModel(app,composeActions,work,ui);
        RequestEvidenceViewModel evidence=new RequestEvidenceViewModel(app,evidenceActions,work,ui);
        RequestComposeFragment fragment=new RequestComposeFragment(factory(catalogue),factory(compose),factory(evidence));
        android.os.Bundle args=new android.os.Bundle(); args.putString("office_id","OFF-REG"); fragment.setArguments(args);
        activity.navigate(fragment,true); idle(); layout(viewportWidth,viewportHeight);
    }
    private void chooseService() {
        view(R.id.composeService).performClick(); idle();
        androidx.appcompat.app.AlertDialog dialog=(androidx.appcompat.app.AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertNotNull(dialog); dialog.getListView().performItemClick(null,0,0); idle();
    }
    private void capture(String name,int width,int height) throws Exception {
        captureView(activity.findViewById(R.id.activityRoot),name,width,height);
    }
    private void captureView(View root,String name,int width,int height) throws Exception {
        android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888);
        java.io.File dir=new java.io.File("build/outputs/screenshots"); assertTrue(dir.isDirectory()||dir.mkdirs());
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,name))) {
            root.draw(new android.graphics.Canvas(image)); assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));
        } finally { image.recycle(); }
    }
    @Test public void studentRequestDetailsCannotOpenTheAssignedOfficePdfUploader() {
        detailActions.result=pdfRecord(); launchDetail(); complete(); assertEquals(View.GONE,view(R.id.requestPdfOpen).getVisibility()); assertEquals(0,pdfActions.reads);
    }
    @Test public void staffPdfPickerUsesOnlyPdfAndDoesNotUploadOnSelection() {
        openPdf(); pdfView(R.id.pdfChoose).performClick();
        org.robolectric.shadows.ShadowActivity.IntentForResult picker=Shadows.shadowOf(activity).getNextStartedActivityForResult(); assertNotNull(picker);
        assertEquals(Intent.ACTION_OPEN_DOCUMENT,picker.intent.getAction()); assertArrayEquals(new String[]{"application/pdf"},picker.intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES));
        Shadows.shadowOf(activity).receiveResult(picker.intent,android.app.Activity.RESULT_OK,new Intent().setData(android.net.Uri.parse("content://fixture/pdf"))); complete();
        assertEquals(0,pdfActions.prepares); assertEquals(0,pdfActions.uploads); assertTrue(((TextView)pdfView(R.id.pdfName)).getText().toString().contains("selected-office.pdf"));
        pdfView(R.id.pdfPrimary).performClick(); idle(); assertEquals(PdfUploadViewModel.Mode.REVIEW,pdfModel().state().getValue().mode); assertEquals(0,pdfActions.prepares);
        assertTrue("Staff must see the actual request reference before confirming",((TextView)pdfView(R.id.pdfReview)).getText().toString().contains("canonical-1"));
    }
    @Test public void onlyExplicitPdfConfirmationPublishesAndRefreshesTheCanonicalRequest() {
        openPdf(); selectPdf(); pdfView(R.id.pdfPrimary).performClick(); pdfView(R.id.pdfPrimary).performClick(); idle();
        assertFalse(pdfView(R.id.pdfPrimary).isEnabled()); assertFalse(pdfView(R.id.pdfBack).isEnabled()); assertFalse(pdfView(R.id.pdfChoose).isEnabled()); complete();
        assertEquals(1,pdfActions.prepares); assertEquals(1,pdfActions.uploads); assertNull(currentDetail().getChildFragmentManager().findFragmentByTag(PdfUploadDialog.TAG));
        complete(); assertEquals("Processing",text(R.id.requestDetailStatus));
    }
    @Test public void uncertainPdfUploadKeepsThePreparedFileAndExplainsTheUnconfirmedOutcome() {
        openPdf(); selectPdf(); pdfActions.uploadError=new java.io.IOException("private server"); pdfView(R.id.pdfPrimary).performClick(); pdfView(R.id.pdfPrimary).performClick(); complete();
        assertTrue(pdfModel().state().getValue().uncertain); assertFalse(pdfView(R.id.pdfChoose).isEnabled()); assertFalse(pdfView(R.id.pdfBack).isEnabled());
        assertTrue(((TextView)pdfView(R.id.pdfReview)).getText().toString().contains("unconfirmed")); assertFalse(((TextView)pdfView(R.id.pdfMessage)).getText().toString().contains("private server"));
        pdfActions.uploadError=null; pdfView(R.id.pdfPrimary).performClick(); complete(); assertEquals(1,pdfActions.prepares); assertEquals(2,pdfActions.uploads); assertEquals(id(71),pdfActions.uploadedId);
    }
    @Test public void unconfiguredPdfUploadBlocksThePickerAndCanReloadActualOptions() {
        pdfActions.configured=false; openPdf(); assertFalse(pdfView(R.id.pdfChoose).isEnabled()); assertFalse(pdfView(R.id.pdfPrimary).isEnabled());
        assertEquals(View.VISIBLE,pdfView(R.id.pdfReload).getVisibility()); pdfActions.configured=true; pdfView(R.id.pdfReload).performClick(); complete(); assertTrue(pdfView(R.id.pdfChoose).isEnabled()); assertEquals(2,pdfActions.reads);
    }
    @Test public void accountReplacementRemovesTheSelectedPdfAndPreventsConfirmation() {
        openPdf(); selectPdf(); pdfActions.current=false; invalidateAccount();
        assertEquals("",((TextView)pdfView(R.id.pdfName)).getText().toString()); assertFalse(pdfView(R.id.pdfPrimary).isEnabled()); assertFalse(pdfView(R.id.pdfChoose).isEnabled());
    }
    @Test public void cancelledPdfPickerLeavesNoSelectionOrUpload() {
        openPdf(); pdfView(R.id.pdfChoose).performClick(); org.robolectric.shadows.ShadowActivity.IntentForResult picker=Shadows.shadowOf(activity).getNextStartedActivityForResult();
        Shadows.shadowOf(activity).receiveResult(picker.intent,android.app.Activity.RESULT_CANCELED,null); idle(); assertNull(pdfModel().state().getValue().intent); assertEquals(0,pdfActions.prepares); assertFalse(pdfView(R.id.pdfPrimary).isEnabled());
    }
    @Test public void anotherOfficeCannotOpenTheCanonicalRequestsPdfUploader() {
        GoServeApplication app=(GoServeApplication)RuntimeEnvironment.getApplication(); ReflectionHelpers.setField(app.auth(),"profile",new ph.edu.tip.goserve.domain.SessionProfile(id(90),"fixture@demo.goserve.test","Fixture staff",ph.edu.tip.goserve.domain.Role.STAFF,"OFF-GCC","guidance",true,1,true,"fixture-terms","fixture-terms","home",null));
        detailActions.result=pdfRecord(); launchDetail(); complete(); assertEquals(View.GONE,view(R.id.requestPdfOpen).getVisibility()); assertEquals(0,pdfActions.reads);
    }
    @Test public void capturePdfSelectionReviewAndUncertainUploadInBothThemes() throws Exception {
        for(boolean dark:new boolean[]{false,true}) {
            RuntimeEnvironment.setQualifiers(dark?"w390dp-h844dp-port-night-mdpi":"w390dp-h844dp-port-notnight-mdpi");
            openPdf(); assertEquals(ph.edu.tip.goserve.domain.Role.STAFF,activity.profile().role); capturePdf("pdf-"+(dark?"dark":"light")+"-empty.png",390,844);
            selectPdf(); capturePdf("pdf-"+(dark?"dark":"light")+"-selected.png",390,844);
            pdfView(R.id.pdfPrimary).performClick(); idle(); capturePdf("pdf-"+(dark?"dark":"light")+"-review.png",390,844);
            pdfActions.uploadError=new java.io.IOException("fixture lost response"); pdfView(R.id.pdfPrimary).performClick(); complete();
            capturePdf("pdf-"+(dark?"dark":"light")+"-uncertain.png",390,844); pdfActions.uploadError=null; controller.close(); controller=null;
        }
    }
    @Test public void captureLargeTextPdfReviewKeepsConfirmationAndFinalCopyReachable() throws Exception {
        viewportWidth=320; viewportHeight=480; RuntimeEnvironment.setFontScale(2f);
        for(boolean dark:new boolean[]{false,true}) {
            RuntimeEnvironment.setQualifiers(dark?"w320dp-h480dp-port-night-mdpi":"w320dp-h480dp-port-notnight-mdpi"); openPdf(); selectPdf();
            pdfView(R.id.pdfPrimary).performClick(); idle(); capturePdf("pdf-large-"+(dark?"dark":"light")+"-top.png",320,480);
            android.graphics.Rect bounds=new android.graphics.Rect(); assertTrue(pdfView(R.id.pdfPrimary).getGlobalVisibleRect(bounds)); assertTrue(bounds.height()>=48);
            androidx.core.widget.NestedScrollView scroll=(androidx.core.widget.NestedScrollView)pdfView(R.id.pdfScroll); scroll.fullScroll(View.FOCUS_DOWN); idle();
            View ending=pdfView(R.id.pdfEdit); ending.requestRectangleOnScreen(new android.graphics.Rect(0,0,ending.getWidth(),ending.getHeight()),true); idle();
            assertTrue(ending.getGlobalVisibleRect(bounds)); assertTrue(bounds.height()>=48); assertTrue(pdfView(R.id.pdfPrimary).getGlobalVisibleRect(bounds)); assertTrue(bounds.height()>=48);
            capturePdf("pdf-large-"+(dark?"dark":"light")+"-end.png",320,480); controller.close(); controller=null;
        }
    }
    private void capturePdf(String name,int width,int height) throws Exception {
        PdfUploadDialog dialog=(PdfUploadDialog)currentDetail().getChildFragmentManager().findFragmentByTag(PdfUploadDialog.TAG);
        View root=dialog.requireView(); root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY)); root.layout(0,0,width,height); idle(); captureView(root,name,width,height);
    }
    private void openPdf() {
        GoServeApplication app=(GoServeApplication)RuntimeEnvironment.getApplication(); ReflectionHelpers.setField(app.auth(),"profile",new ph.edu.tip.goserve.domain.SessionProfile(id(90),"fixture@demo.goserve.test","Fixture staff",ph.edu.tip.goserve.domain.Role.STAFF,"OFF-REG",null,true,1,true,"fixture-terms","fixture-terms","home",null));
        detailActions.result=pdfRecord(); launchDetail(); complete(); view(R.id.requestPdfOpen).performClick(); idle(); complete();
    }
    private PdfUploadViewModel pdfModel() { return new ViewModelProvider(currentDetail().getChildFragmentManager().findFragmentByTag(PdfUploadDialog.TAG)).get(PdfUploadViewModel.class); }
    private RequestDetailFragment currentDetail() { return (RequestDetailFragment)activity.getSupportFragmentManager().findFragmentById(R.id.mainContainer); }
    private View pdfView(int id) { androidx.fragment.app.Fragment f=currentDetail().getChildFragmentManager().findFragmentByTag(PdfUploadDialog.TAG); assertNotNull(f); View v=f.requireView().findViewById(id); assertNotNull("Missing PDF control: "+id,v); return v; }
    private void selectPdf() { pdfModel().select(new PdfSource()); complete(); }
    private static RequestData.Record pdfRecord() { return new RequestData.Record(id(1),"canonical-1",id(92),"OFF-REG","digital","2026-10-21T01:00:00Z",2,RequestStatus.PROCESSING,new RequestData.Service(id(50),"OFF-REG","Configured service",1,FeeDeclaration.FREE,BigDecimal.ZERO,null,List.of("digital"),List.of(),true)); }
    private static final class PdfSource implements ph.edu.tip.goserve.data.EvidenceInput.Source {
        public String name() { return "selected-office.pdf"; } public String mimeType() { return "application/pdf"; } public Long declaredSize() { return 4L; } public java.io.InputStream open() { return new java.io.ByteArrayInputStream(new byte[]{1,2,3,4}); }
    }
    private static final class PdfActions implements PdfUploadViewModel.Actions {
        boolean current=true,configured=true; int reads,prepares,uploads; Exception uploadError; String uploadedId;
        public boolean allowed() { return true; } public String officeId() { return "OFF-REG"; } public void requireCurrent() { if(!current)throw new IllegalStateException("STALE_ACCOUNT"); }
        public RequestData.Record request(String id) { ++reads; return pdfRecord(); }
        public RequestData.RequestOptions options(RequestData.Record request) { return new RequestData.RequestOptions(request.id,request.version,java.util.Map.of("deliverable_upload",new RequestData.FileOptions(configured,configured?null:"CONFIGURATION_REQUIRED",configured?List.of("application/pdf"):null,configured?4L:null))); }
        public ph.edu.tip.goserve.data.EvidenceInput.Selection read(ph.edu.tip.goserve.data.EvidenceInput.Source source,RequestData.FileOptions policy) throws Exception { return ph.edu.tip.goserve.data.EvidenceInput.read(new PdfSource(),policy,4); }
        public ph.edu.tip.goserve.domain.DeliveryData.PdfUpload input(RequestData.Record request,RequestData.RequestOptions options,ph.edu.tip.goserve.data.EvidenceInput.Selection file,ph.edu.tip.goserve.domain.DeliveryData.PdfUpload previous) { return new ph.edu.tip.goserve.domain.DeliveryData.PdfUpload(request,options,id(90),"OFF-REG",4,"a".repeat(64),id(72),null); }
        public ph.edu.tip.goserve.domain.DeliveryData.PdfUpload prepare(ph.edu.tip.goserve.domain.DeliveryData.PdfUpload input) { ++prepares; return new ph.edu.tip.goserve.domain.DeliveryData.PdfUpload(input.request,input.options,input.actorId,input.officeId,input.sizeBytes,input.contentSha256,input.operationKey,id(71)); }
        public ph.edu.tip.goserve.domain.DeliveryData.File upload(ph.edu.tip.goserve.domain.DeliveryData.PdfUpload input,ph.edu.tip.goserve.data.EvidenceInput.Selection file) throws Exception { ++uploads; uploadedId=input.fileId; if(uploadError!=null)throw uploadError; return new ph.edu.tip.goserve.domain.DeliveryData.File(id(71),id(92),id(50),id(1),ph.edu.tip.goserve.domain.DeliveryData.FileKind.DELIVERABLE,null,"application/pdf",4,"verified",1,true,"2026-10-21T01:00:00Z",null); }
    }
    private void launchList() {
        if(listActions.office!=null) {
            GoServeApplication app=(GoServeApplication)RuntimeEnvironment.getApplication();
            ReflectionHelpers.setField(app.auth(),"profile",new ph.edu.tip.goserve.domain.SessionProfile(id(90),"fixture@demo.goserve.test","Fixture staff",
                ph.edu.tip.goserve.domain.Role.STAFF,listActions.office,listActions.office.startsWith("DEP-")?"secretary":null,
                true,1,true,"fixture-terms","fixture-terms","home",null));
        }
        launch(); GoServeApplication app=(GoServeApplication)activity.getApplication();
        if(listActions.office!=null) { assertEquals(ph.edu.tip.goserve.domain.Role.STAFF,activity.profile().role);assertEquals(ConsultationViewModel.permitted(activity.profile())?5:4,((CapsuleNavigationView)view(R.id.bottomNavigation)).getMenu().size());assertEquals(ConsultationViewModel.permitted(activity.profile()),((CapsuleNavigationView)view(R.id.bottomNavigation)).getMenu().findItem(R.id.nav_consult)!=null); }
        RequestListViewModel vm=new RequestListViewModel(app,listActions,work,ui);
        ((CapsuleNavigationView)activity.findViewById(R.id.bottomNavigation)).markSelectedItemId(R.id.nav_requests);
        activity.navigate(new RequestListFragment(factory(vm)),false); idle(); layout(viewportWidth,viewportHeight);
    }
    private void launchDetail() {
        launch(); GoServeApplication app=(GoServeApplication)activity.getApplication();
        RequestDetailViewModel vm=new RequestDetailViewModel(app,detailActions,work,ui);
        RequestActionViewModel actions=new RequestActionViewModel(app,actionActions,work,ui);
        PdfUploadViewModel pdf=new PdfUploadViewModel(app,pdfActions,work,ui);
        RequestDetailFragment fragment=new RequestDetailFragment(factory(vm),factory(actions),factory(pdf)); android.os.Bundle args=new android.os.Bundle(); args.putString("request_id",id(1)); fragment.setArguments(args);
        ((CapsuleNavigationView)activity.findViewById(R.id.bottomNavigation)).markSelectedItemId(R.id.nav_requests);
        activity.navigate(fragment,false); idle(); layout(viewportWidth,viewportHeight);
    }
    private static ViewModelProvider.Factory factory(ViewModel vm) { return new ViewModelProvider.Factory() { @Override public <T extends ViewModel> T create(Class<T> type) { return type.cast(vm); } }; }
    private void launch() {
        GoServeApplication app=(GoServeApplication)RuntimeEnvironment.getApplication();
        controller=Robolectric.buildActivity(MainActivity.class,new Intent(app,MainActivity.class)).setup().visible(); activity=controller.get(); idle();
    }
    private void complete() { work.run(); ui.run(); idle(); layout(viewportWidth,viewportHeight); }
    private void invalidateAccount() { ((androidx.lifecycle.MutableLiveData<Long>)((GoServeApplication)activity.getApplication()).accountInvalidations()).setValue(500L); idle(); layout(390,844); }
    private View view(int id) { View value=activity.findViewById(id); assertNotNull("Missing request control: "+id,value); return value; }
    private String text(int id) { return ((TextView)view(id)).getText().toString(); }
    private void idle() { Shadows.shadowOf(Looper.getMainLooper()).idle(); activity.getSupportFragmentManager().executePendingTransactions(); }
    private void layout(int width,int height) { View root=activity.findViewById(R.id.activityRoot); root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY)); root.layout(0,0,width,height); idle(); }
    private static String id(int n) { return String.format(java.util.Locale.ROOT,"00000000-0000-4000-8000-%012d",n); }
    private static RequestData.Record record(int n,FeeDeclaration fee) {
        return new RequestData.Record(id(n),"canonical-"+n,id(90),"OFF-REG","physical","2026-10-21T01:00:00Z",2,
            fee==FeeDeclaration.REQUIRED?RequestStatus.AWAITING_PAYMENT:RequestStatus.SUBMITTED,
            new RequestData.Service(id(50),"OFF-REG","Configured service",1,fee,fee==FeeDeclaration.REQUIRED?new BigDecimal("35.00"):null,"PHP",List.of("physical"),List.of(),true));
    }
    private static PaymentData.Record paid() { return new PaymentData.Record(id(90),id(2),id(1),PaymentMethod.CASH,PaymentStatus.PAID,new BigDecimal("35.00"),"PHP",2,id(91),"2026-10-21T01:00:00Z"); }
    private static final class ListActions implements RequestListViewModel.Actions {
        boolean current=true; int reads; String office,cursorAt,cursorId; RequestStatus filter; Exception error;
        RequestData.RequestPage page=new RequestData.RequestPage(List.of(record(1,FeeDeclaration.PENDING_REVIEW)),null,null);
        public boolean allowed() { return true; } public String officeId() { return office; }
        public void requireCurrent() { if(!current)throw new IllegalStateException("STALE_ACCOUNT"); }
        public RequestData.RequestPage page(RequestStatus filter,String createdAt,String id) throws Exception { ++reads; this.filter=filter; cursorAt=createdAt; cursorId=id; if(error!=null)throw error; return page; }
    }
    private static RequestData.Service formService(FeeDeclaration fee) { return new RequestData.Service(id(50),"OFF-REG","Configured service",1,fee,fee==FeeDeclaration.REQUIRED?new BigDecimal("35.00"):BigDecimal.ZERO,fee==FeeDeclaration.REQUIRED?"PHP":null,List.of("physical"),List.of(),true); }
    private static RequestData.Quote formQuote(FeeDeclaration fee) { return new RequestData.Quote(id(70),id(90),"physical",formService(fee),true); }
    private static final class CatalogueActions implements ServiceCatalogueViewModel.Actions {
        boolean current=true; int reads; Exception error; RequestData.ServicePage page=new RequestData.ServicePage(List.of(formService(FeeDeclaration.FREE)),null);
        public boolean allowed() { return true; } public void requireCurrent() { if(!current)throw new IllegalStateException("STALE_ACCOUNT"); }
        public RequestData.ServicePage services(String office,String cursor) throws Exception { ++reads; if(error!=null)throw error; return page; }
    }
    private static final class ComposeActions implements RequestComposeViewModel.Actions {
        boolean current=true,uploads,egovpay; String uploadMime="application/pdf"; int submits; Exception error,quoteError; RequestData.Submission submitted; RequestData.Quote quote=formQuote(FeeDeclaration.FREE);
        public boolean allowed() { return true; } public void requireCurrent() { if(!current)throw new IllegalStateException("STALE_ACCOUNT"); }
        public RequestData.Quote quote(String service,String delivery) throws Exception { if(quoteError!=null)throw quoteError; return quote; }
        public RequestData.QuoteOptions options(RequestData.Quote quote) { return new RequestData.QuoteOptions(quote.id,quote.service.version,new RequestData.ActionOptions(true,null),
            uploads?new RequestData.FileOptions(true,null,List.of(uploadMime),12L):new RequestData.FileOptions(false,"CONFIGURATION_REQUIRED",null,null),egovpay); }
        public RequestData.Submission draft(RequestData.Quote quote,RequestData.QuoteOptions options,List<RequestData.Evidence> files,String notes,String method,RequestData.Submission previous) throws Exception {
            if(quote.service.fee==FeeDeclaration.REQUIRED && !"cash".equals(method))throw new RequestRepository.WorkflowException("VALIDATION_FAILED");
            return new RequestData.Submission(quote,options,files,notes,method,id(200));
        }
        public RequestData.Record submit(RequestData.Submission draft) throws Exception {
            ++submits; submitted=draft; if(error!=null)throw error;
            return new RequestData.Record(id(80),"canonical-created",id(90),"OFF-REG","physical","2026-10-21T01:00:00Z",2,RequestStatus.SUBMITTED,draft.quote.service);
        }
    }
    private static final class EvidenceActions implements RequestEvidenceViewModel.Actions {
        boolean current=true; int prepares,uploads; Exception error,readError;
        public boolean allowed() { return true; } public String accountId() { return id(90); }
        public void requireCurrent() { if(!current)throw new IllegalStateException("STALE_ACCOUNT"); }
        public ph.edu.tip.goserve.data.EvidenceInput.Selection read(ph.edu.tip.goserve.data.EvidenceInput.Source source,RequestData.FileOptions policy) throws Exception {
            if(readError!=null)throw readError;
            return ph.edu.tip.goserve.data.EvidenceInput.read(new ph.edu.tip.goserve.data.EvidenceInput.Source() {
                public String name() { return "private-evidence.pdf"; } public String mimeType() { return "application/pdf"; } public Long declaredSize() { return 4L; }
                public java.io.InputStream open() { return new java.io.ByteArrayInputStream(new byte[]{1,2,3,4}); }
            },policy,20);
        }
        public RequestData.PreparedUpload input(RequestData.Quote quote,RequestData.QuoteOptions options,String requirement,ph.edu.tip.goserve.data.EvidenceInput.Selection file,RequestData.PreparedUpload previous) {
            return new RequestData.PreparedUpload(quote,options,requirement,file.mimeType,file.bytes().length,"a".repeat(64),id(200),null);
        }
        public RequestData.PreparedUpload prepare(RequestData.PreparedUpload input) { ++prepares;
            return new RequestData.PreparedUpload(input.quote,input.options,input.requirement,input.mimeType,input.sizeBytes,input.contentSha256,input.operationKey,id(5)); }
        public RequestData.Evidence upload(RequestData.PreparedUpload input,ph.edu.tip.goserve.data.EvidenceInput.Selection file) throws Exception {
            ++uploads; if(error!=null)throw error; return new RequestData.Evidence(id(5),id(90),id(50),null,input.requirement,"verified",true);
        }
    }
    private static final class ActionActions implements RequestActionViewModel.Actions {
        boolean current=true,cancel; String office; int writes; Exception error,readError; RequestData.Transition committed;
        public boolean allowed() { return true; } public String officeId() { return office; }
        public void requireCurrent() { if(!current)throw new IllegalStateException("STALE_ACCOUNT"); }
        public RequestData.RequestOptions options(RequestData.Record request) throws Exception { if(readError!=null)throw readError; return new RequestData.RequestOptions(request.id,request.version,
            java.util.Map.of("cancel",new RequestData.ActionOptions(cancel,cancel?null:"CONFIGURATION_REQUIRED"))); }
        public RequestData.Transition draft(RequestData.Record request,RequestData.RequestOptions options,RequestStatus target,String reason,RequestData.Transition previous) throws Exception {
            if((target==RequestStatus.CANCELLED || target==RequestStatus.REJECTED) && reason.isBlank())throw new RequestRepository.WorkflowException("VALIDATION_FAILED");
            return new RequestData.Transition(request,options,id(91),office,target,reason,previous==null?id(200):previous.operationKey);
        }
        public RequestData.Record commit(RequestData.Transition input) throws Exception {
            ++writes; committed=input; if(error!=null)throw error; RequestData.Record old=input.request;
            return new RequestData.Record(old.id,old.reference,old.ownerId,old.officeId,old.deliveryMode,old.createdAt,old.version+1,input.target,old.snapshot);
        }
    }
    private static final class DetailActions implements RequestDetailViewModel.Actions {
        boolean current=true; int reads; String requestedId,paymentRequest; Exception error,paymentError; PaymentData.Record payment;
        RequestData.Record result=record(1,FeeDeclaration.REQUIRED);
        public boolean allowed() { return true; } public void requireCurrent() { if(!current)throw new IllegalStateException("STALE_ACCOUNT"); }
        public RequestData.Record request(String id) throws Exception { ++reads; requestedId=id; if(error!=null)throw error; return result; }
        public PaymentData.Record payment(String id) throws Exception { paymentRequest=id; if(paymentError!=null)throw paymentError; return payment; }
    }
    private static final class Queue implements Executor {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>(); public void execute(Runnable task) { tasks.add(task); }
        int size() { return tasks.size(); } void run() { assertFalse("Expected queued request read",tasks.isEmpty()); tasks.remove().run(); }
    }
    private static final class Session implements RealtimeDataSource.Session {
        Set<String> tables; RealtimeDataSource.Listener listener; int closed;
        public long generation() { return 1; } public void start() {} public void stop() {} public void refreshAuthorization() {} public void close() {}
        public RealtimeDataSource.Subscription subscribe(Set<String> tables,RealtimeDataSource.Listener listener) { this.tables=tables; this.listener=listener; return ()->++closed; }
        void invalidate() { listener.onInvalidated(ReflectionHelpers.callConstructor(RealtimeDataSource.Invalidation.class,
            ReflectionHelpers.ClassParameter.from(long.class,1L),ReflectionHelpers.ClassParameter.from(Set.class,tables),
            ReflectionHelpers.ClassParameter.from(RealtimeDataSource.Reason.class,RealtimeDataSource.Reason.SNAPSHOT_REQUIRED))); }
    }
}
