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
import ph.edu.tip.goserve.data.PrivateDocument;
import ph.edu.tip.goserve.data.RealtimeDataSource;
import ph.edu.tip.goserve.domain.DeliveryData;
import ph.edu.tip.goserve.domain.FeeDeclaration;
import ph.edu.tip.goserve.domain.RequestData;
import ph.edu.tip.goserve.domain.RequestStatus;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=GoServeApplication.class,qualifiers="w390dp-h844dp-port-notnight-mdpi",
    shadows={AccountIntegrationTest.Store.class,AccountIntegrationTest.Server.class})
@LooperMode(LooperMode.Mode.PAUSED) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class RequestFilesInteractionTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private RequestFilesFragment fragment;
    private RequestFilesViewModel files;
    private EvidenceViewerViewModel viewer;
    private int viewportWidth=390,viewportHeight=844;
    private final FilesActions fileActions=new FilesActions();
    private final ViewerActions viewerActions=new ViewerActions();
    private final Queue work=new Queue(),ui=new Queue();
    private final Session session=new Session();
    @Before public void setup() throws Exception {
        ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory.class,"_instance",null);
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
    @Test public void onlyCanonicalSuccessfulFileReadsCanShowRowsOrEmpty() {
        fileActions.page=new DeliveryData.FilePage(List.of(),null); launch();
        assertEquals(View.VISIBLE,view(R.id.requestFilesLoading).getVisibility()); assertEquals(View.GONE,view(R.id.requestFilesEmpty).getVisibility());
        assertEquals(0,fileActions.reads); complete();
        assertEquals(View.VISIBLE,view(R.id.requestFilesEmpty).getVisibility()); assertEquals(View.GONE,view(R.id.requestFilesError).getVisibility());
        assertTrue(text(R.id.requestFilesCaption).contains("fixture-reference")); assertFalse(text(R.id.requestFilesCaption).contains("REQ-2026"));
    }
    @Test public void aReadFailureShowsRetryInsteadOfAFalseEmptyOrFigmaExample() {
        fileActions.error=new java.io.IOException("private host failure"); launch(); complete();
        assertEquals(View.VISIBLE,view(R.id.requestFilesError).getVisibility()); assertEquals(View.GONE,view(R.id.requestFilesEmpty).getVisibility());
        assertFalse(text(R.id.requestFilesError).contains("private host")); assertTrue(view(R.id.requestFilesRetry).isEnabled());
        fileActions.error=null; view(R.id.requestFilesRetry).performClick(); complete(); assertEquals(2,fileActions.reads); assertEquals(id(1),text(R.id.evidenceFileKey));
    }
    @Test public void evidenceRowOpensOnlyAfterExplicitTapAndACompletedPrivateTransfer() {
        launch(); complete(); assertEquals(0,viewerActions.reads);
        row().performClick(); idle(); assertEquals(0,viewerActions.reads);
        assertEquals(View.VISIBLE,view(R.id.evidenceViewerLoading).getVisibility()); complete();
        org.robolectric.shadows.ShadowActivity.IntentForResult started=Shadows.shadowOf(activity).getNextStartedActivityForResult();
        assertNotNull(started); assertEquals(Intent.ACTION_VIEW,started.intent.getAction()); assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION,started.intent.getFlags());
        assertEquals("application/pdf",started.intent.getType()); assertEquals(viewerActions.document.uri(),started.intent.getData());
        assertEquals(EvidenceViewerViewModel.Mode.OPEN,viewer.state().getValue().mode);
    }
    @Test public void returningFromExternalViewerDeletesThePrivateCopy() {
        launch(); complete(); row().performClick(); complete();
        org.robolectric.shadows.ShadowActivity.IntentForResult started=Shadows.shadowOf(activity).getNextStartedActivityForResult(); assertNotNull(started);
        Shadows.shadowOf(activity).receiveResult(started.intent,android.app.Activity.RESULT_CANCELED,null); idle();
        assertTrue(viewerActions.document.closed); assertEquals(EvidenceViewerViewModel.Mode.IDLE,viewer.state().getValue().mode);
        assertEquals(View.GONE,view(R.id.evidenceViewerStatus).getVisibility());
    }
    @Test public void recreatedFileViewDoesNotLaunchTheViewerAgain() {
        launch(); complete(); row().performClick(); complete(); assertNotNull(Shadows.shadowOf(activity).getNextStartedActivityForResult());
        activity.getSupportFragmentManager().beginTransaction().detach(fragment).commitNow();
        activity.getSupportFragmentManager().beginTransaction().attach(fragment).commitNow(); idle(); layout(390,844);
        assertNull(Shadows.shadowOf(activity).getNextStartedActivityForResult()); assertEquals(1,viewerActions.reads);
        assertEquals(EvidenceViewerViewModel.Mode.OPEN,viewer.state().getValue().mode);
    }
    @Test public void replacedAccountClearsPrivateRowsAndCancelsAPendingOpen() {
        launch(); complete(); row().performClick(); work.run(); fileActions.current=false; viewerActions.current=false;
        ((androidx.lifecycle.MutableLiveData<Long>)((GoServeApplication)activity.getApplication()).accountInvalidations()).setValue(500L); ui.run(); idle(); layout(390,844);
        assertTrue(viewerActions.document.closed); assertNull(activity.findViewById(R.id.evidenceFileTitle));
        assertEquals(RequestFilesViewModel.Mode.UNAVAILABLE,files.state().getValue().mode); assertNull(Shadows.shadowOf(activity).getNextStartedActivityForResult());
    }
    @Test public void privateFileAndRequestInvalidationsRefreshAndSubscriptionCloses() {
        launch(); complete(); assertEquals(Set.of("requests","payments","request_files"),session.tables);
        session.invalidate(); complete(); assertEquals(2,fileActions.reads);
        activity.navigate(new MoreFragment(),false); idle(); assertTrue(session.closed>0);
    }
    @Test public void failedTransferCanBeRetriedWithoutClaimingSavedDelivery() {
        viewerActions.error=new java.io.IOException("private detail"); launch(); complete(); row().performClick(); complete();
        assertEquals(View.VISIBLE,view(R.id.evidenceViewerRetry).getVisibility()); assertFalse(text(R.id.evidenceViewerStatus).contains("private detail"));
        assertNull(Shadows.shadowOf(activity).getNextStartedActivityForResult());
        viewerActions.error=null; view(R.id.evidenceViewerRetry).performClick(); complete(); assertEquals(2,viewerActions.reads); assertNotNull(Shadows.shadowOf(activity).getNextStartedActivityForResult());
    }
    @Test public void narrowLargeTextKeepsTheReturnActionReachable() {
        viewportWidth=320; viewportHeight=480; RuntimeEnvironment.setFontScale(2f); RuntimeEnvironment.setQualifiers("w320dp-h480dp-port-notnight-mdpi"); launch(); complete(); layout(320,480);
        View back=view(R.id.requestFilesReturn); android.graphics.Rect bounds=new android.graphics.Rect(); assertTrue(back.getGlobalVisibleRect(bounds));
        assertTrue(bounds.height()>=48); assertTrue(bounds.width()>=48); assertTrue(back.isEnabled());
        RecyclerView list=(RecyclerView)view(R.id.requestFilesList); list.scrollToPosition(list.getAdapter().getItemCount()-1); idle(); layout(320,480);
        assertTrue(view(R.id.requestFilesNotice).isShown()); assertEquals(View.GONE,view(R.id.bottomNavigation).getVisibility());
    }
    @Test public void canonicalRequestDetailsNavigateToEvidenceWithoutExposingTheDeliveryShortcut() {
        launch(true); complete();
        assertEquals(View.GONE,view(R.id.requestFileAction).getVisibility());
        assertEquals(View.VISIBLE,view(R.id.requestEvidenceOpen).getVisibility()); view(R.id.requestEvidenceOpen).performClick(); idle();
        androidx.fragment.app.Fragment current=activity.getSupportFragmentManager().findFragmentById(R.id.mainContainer);
        assertTrue(current instanceof RequestFilesFragment); assertEquals(id(70),current.getArguments().getString("request_id"));
        complete(); assertEquals(id(1),text(R.id.evidenceFileKey));
    }
    @Test public void serverRevocationAfterOpeningRemovesThePrivateCopyAndRows() {
        launch(); complete(); row().performClick(); complete(); assertNotNull(Shadows.shadowOf(activity).getNextStartedActivityForResult());
        fileActions.error=new ph.edu.tip.goserve.data.RequestRepository.WorkflowException("FORBIDDEN"); session.invalidate(); complete();
        assertTrue(viewerActions.document.closed); assertNull(activity.findViewById(R.id.evidenceFileTitle)); assertEquals(RequestFilesViewModel.Mode.UNAVAILABLE,files.state().getValue().mode);
    }
    @Test public void missingAndroidViewerShowsRecoveryAndRemovesThePrivateCopy() {
        launch(); complete(); Shadows.shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true);
        row().performClick(); complete();
        assertTrue(viewerActions.document.closed); assertEquals("NO_VIEWER",viewer.state().getValue().error);
        assertEquals(View.VISIBLE,view(R.id.evidenceViewerRetry).getVisibility()); assertTrue(text(R.id.evidenceViewerStatus).contains("No viewer"));
    }
    @Test public void capturePrivateEvidenceAndRetryStatesInBothThemes() throws Exception {
        for(boolean dark:new boolean[]{false,true}) {
            RuntimeEnvironment.setQualifiers(dark?"w390dp-h844dp-port-night-mdpi":"w390dp-h844dp-port-notnight-mdpi");
            GoServeApplication app=(GoServeApplication)RuntimeEnvironment.getApplication();
            ReflectionHelpers.setField(app.auth(),"profile",new ph.edu.tip.goserve.domain.SessionProfile(id(90),"fixture@demo.goserve.test","Fixture staff",
                ph.edu.tip.goserve.domain.Role.STAFF,"OFF-REG",null,true,1,true,"fixture-terms","fixture-terms","home",null));
            DeliveryData.File first=file(); DeliveryData.File second=new DeliveryData.File(id(2),first.ownerId,first.serviceId,first.requestId,first.kind,null,"image/jpeg",1024,"verified",2,true,first.createdAt,null);
            fileActions.page=new DeliveryData.FilePage(List.of(first,second),null); launch(); complete();
            assertEquals(ph.edu.tip.goserve.domain.Role.STAFF,activity.profile().role); capture("evidence-"+(dark?"dark":"light")+"-list.png",390,844);
            viewerActions.error=new java.io.IOException("fixture unavailable"); row().performClick(); complete(); capture("evidence-"+(dark?"dark":"light")+"-error.png",390,844);
            fileActions.page=new DeliveryData.FilePage(List.of(),null); view(R.id.requestFilesRefresh).performClick(); complete(); capture("evidence-"+(dark?"dark":"light")+"-empty.png",390,844);
            viewerActions.error=null; controller.close(); controller=null;
        }
    }
    @Test public void captureLargeTextEvidenceWithPinnedReturnInBothThemes() throws Exception {
        viewportWidth=320; viewportHeight=480; RuntimeEnvironment.setFontScale(2f);
        for(boolean dark:new boolean[]{false,true}) {
            RuntimeEnvironment.setQualifiers(dark?"w320dp-h480dp-port-night-mdpi":"w320dp-h480dp-port-notnight-mdpi");
            fileActions.page=new DeliveryData.FilePage(List.of(file()),null); launch(); complete(); layout(320,480);
            TextView caption=(TextView)view(R.id.requestFilesCaption); RecyclerView evidence=(RecyclerView)view(R.id.requestFilesList);
            assertTrue("Caption must fit the actual narrow list width",caption.getWidth()<=evidence.getWidth()-evidence.getPaddingLeft()-evidence.getPaddingRight());
            assertEquals(caption.getText().length(),caption.getLayout().getLineEnd(caption.getLayout().getLineCount()-1));
            capture("evidence-large-"+(dark?"dark":"light")+"-top.png",320,480);
            RecyclerView list=(RecyclerView)view(R.id.requestFilesList); list.scrollToPosition(list.getAdapter().getItemCount()-1); idle(); layout(320,480);
            View notice=view(R.id.requestFilesNotice); android.graphics.Rect requested=new android.graphics.Rect(0,Math.max(0,notice.getHeight()-40),notice.getWidth(),notice.getHeight());
            notice.requestRectangleOnScreen(requested,true); idle(); layout(320,480);
            android.graphics.Rect visible=new android.graphics.Rect(); assertTrue(notice.getGlobalVisibleRect(visible));
            assertTrue(visible.height()>0); assertTrue(view(R.id.requestFilesReturn).getGlobalVisibleRect(visible)); assertTrue(visible.height()>=48);
            capture("evidence-large-"+(dark?"dark":"light")+"-end.png",320,480); controller.close(); controller=null;
        }
    }
    private void capture(String name,int width,int height) throws Exception {
        layout(width,height); android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888);
        java.io.File dir=new java.io.File("build/outputs/screenshots"); assertTrue(dir.isDirectory()||dir.mkdirs());
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,name))) {
            activity.findViewById(R.id.activityRoot).draw(new android.graphics.Canvas(image)); assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));
        } finally { image.recycle(); }
    }
    private void launch() {
        launch(false);
    }
    private void launch(boolean detail) {
        GoServeApplication app=(GoServeApplication)RuntimeEnvironment.getApplication();
        controller=Robolectric.buildActivity(MainActivity.class,new Intent(app,MainActivity.class)).setup().visible(); activity=controller.get(); idle();
        files=new RequestFilesViewModel(app,fileActions,work,ui); viewer=new EvidenceViewerViewModel(app,viewerActions,work,ui);
        ViewModelProvider.Factory fileFactory=new ViewModelProvider.Factory() { @Override public <T extends ViewModel>T create(Class<T> type) { return type.cast(type==RequestFilesViewModel.class?files:viewer); }};
        fragment=new RequestFilesFragment(fileFactory);
        android.os.Bundle args=new android.os.Bundle(); args.putString("request_id",id(70)); fragment.setArguments(args);
        androidx.fragment.app.Fragment destination=fragment;
        if(detail) {
            RequestDetailViewModel model=new RequestDetailViewModel(app,new RequestDetailViewModel.Actions() {
                public boolean allowed() { return true; } public void requireCurrent() { fileActions.requireCurrent(); }
                public RequestData.Record request(String id) { return fileActions.request(id); }
                public ph.edu.tip.goserve.domain.PaymentData.Record payment(String id) { return null; }
            },work,ui);
            destination=new RequestDetailFragment(new ViewModelProvider.Factory() { @Override public <T extends ViewModel>T create(Class<T> type) { return type.cast(model); }},null,fileFactory);
            destination.setArguments(args);
        }
        activity.navigate(destination,false); idle(); layout(viewportWidth,viewportHeight);
    }
    private View row() { return (View)view(R.id.evidenceFileTitle).getParent(); }
    private void complete() { work.run(); ui.run(); idle(); layout(viewportWidth,viewportHeight); }
    private View view(int id) { View value=activity.findViewById(id); assertNotNull("Missing evidence control: "+id,value); return value; }
    private String text(int id) { return ((TextView)view(id)).getText().toString(); }
    private void idle() { Shadows.shadowOf(Looper.getMainLooper()).idle(); activity.getSupportFragmentManager().executePendingTransactions(); }
    private void layout(int w,int h) { View root=activity.findViewById(R.id.activityRoot); root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY)); root.layout(0,0,w,h); idle(); }
    private static String id(int n) { return String.format(java.util.Locale.ROOT,"00000000-0000-4000-8000-%012d",n); }
    private static DeliveryData.File file() { return new DeliveryData.File(id(1),id(90),id(60),id(70),DeliveryData.FileKind.EVIDENCE,null,"application/pdf",4,"verified",1,true,"2026-10-21T01:00:00Z",null); }
    private static final class FilesActions implements RequestFilesViewModel.Actions {
        boolean current=true; int reads; Exception error; DeliveryData.FilePage page=new DeliveryData.FilePage(List.of(file()),null);
        public boolean allowed() { return true; } public void requireCurrent() { if(!current)throw new IllegalStateException("STALE_ACCOUNT"); }
        public RequestData.Record request(String id) { return new RequestData.Record(id(70),"fixture-reference",id(90),"OFF-REG","physical","2026-10-21T01:00:00Z",2,RequestStatus.PROCESSING,
            new RequestData.Service(id(60),"OFF-REG","Configured fixture",1,FeeDeclaration.FREE,BigDecimal.ZERO,null,List.of("physical"),List.of(),true)); }
        public DeliveryData.FilePage files(RequestData.Record request,DeliveryData.FileKind kind,String after) throws Exception { ++reads; if(error!=null)throw error; return page; }
    }
    private static final class ViewerActions implements EvidenceViewerViewModel.Actions {
        boolean current=true; int reads; Exception error; final Document document=new Document();
        public boolean allowed() { return true; } public void requireCurrent() { if(!current)throw new IllegalStateException("STALE_ACCOUNT"); }
        public PrivateDocument download(DeliveryData.File file) throws Exception { ++reads; if(error!=null)throw error; return document; }
    }
    private static final class Document implements PrivateDocument {
        boolean closed; public android.net.Uri uri() { return android.net.Uri.parse("content://ph.edu.tip.goserve.documents/review/fixture.pdf"); }
        public String mimeType() { return "application/pdf"; } public java.io.OutputStream openOutput() { throw new AssertionError("Repository owns transfer"); }
        public void close() { closed=true; }
    }
    private static final class Queue implements Executor {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>(); public void execute(Runnable task) { tasks.add(task); }
        void run() { assertFalse("Expected queued private-file task",tasks.isEmpty()); tasks.remove().run(); }
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
