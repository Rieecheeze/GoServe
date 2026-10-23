package ph.edu.tip.goserve.ui;

import android.app.Application;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import ph.edu.tip.goserve.domain.InquiryData;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34,application=Application.class)
public final class NotificationsViewModelTest {
    static final String TIME="2026-10-22T00:00:00Z";
    @Test public void loadingDoesNotAcknowledgeAndUsesIndependentGlobalTotal() {
        Fixture f=new Fixture(); f.open(); assertEquals(900,f.state().counts.notifications);assertEquals(1,f.state().page.items.size());assertTrue(f.actions.acks.isEmpty());
    }
    @Test public void openingTargetDoesNotAcknowledgeAndOnlyCurrentPageIdentityCanOpen() {
        Fixture f=new Fixture();f.open();InquiryData.NotificationPage original=f.state().page;
        assertTrue(f.vm.canOpen(original,"notice1"));assertFalse(f.vm.canOpen(original,"forged"));assertTrue(f.actions.acks.isEmpty());
        f.vm.refresh();f.run();assertFalse(f.vm.canOpen(original,"notice1"));
    }
    @Test public void explicitAcknowledgementAcceptsReplayZeroThenReloadsPageAndCounts() {
        Fixture f=new Fixture();f.open();f.vm.acknowledge(f.state().page,"notice1");assertTrue(f.state().acknowledging);f.run();
        assertEquals(List.of("notice1"),f.actions.acks);assertNotNull(f.state().page.items.get(0).readAt);assertEquals(899,f.state().counts.notifications);
    }
    @Test public void oldPageCannotAcknowledgeAfterPaginationAndForgedIdsDoNothing() {
        Fixture f=new Fixture();f.open();InquiryData.NotificationPage original=f.state().page;f.vm.loadMore();f.run();
        assertEquals("notice2",f.state().page.items.get(0).id);assertEquals(TIME,f.actions.afterTime);assertEquals("notice1",f.actions.afterId);
        f.vm.acknowledge(original,"notice1");f.vm.acknowledge(f.state().page,"forged");assertEquals(0,f.work.jobs.size());assertTrue(f.actions.acks.isEmpty());
    }
    @Test public void unknownAcknowledgementKeepsUnreadPageAndExplicitRetryUsesItsId() {
        Fixture f=new Fixture();f.open();InquiryData.NotificationPage original=f.state().page;f.actions.failAck=true;
        f.vm.acknowledge(original,"notice1");f.run();assertSame(original,f.state().page);assertNull(f.state().page.items.get(0).readAt);assertEquals("NETWORK_ERROR",f.state().error);
        f.actions.failAck=false;f.vm.acknowledge(original,"notice1");f.run();assertEquals(List.of("notice1","notice1"),f.actions.acks);
    }
    @Test public void accountReplacementBeforeLoadOrAckPublicationErasesAllPrivateState() {
        for(boolean ack:new boolean[]{false,true}) {
            Fixture f=new Fixture();f.open();if(ack)f.vm.acknowledge(f.state().page,"notice1");else f.vm.refresh();
            f.work.run();f.actions.current=false;f.ui.run();assertTrue(f.state().blocked);assertNull(f.state().page);assertNull(f.state().counts);assertEquals("STALE_ACCOUNT",f.state().error);
        }
    }
    @Test public void invalidationsCoalesceAndFailureDoesNotPresentZeroUnread() {
        Fixture f=new Fixture();f.vm.ensureLoaded();f.vm.refresh();f.vm.refresh();f.run();assertEquals(1,f.work.jobs.size());f.run();assertEquals(2,f.actions.loads);
        f.actions.failLoad=true;f.vm.refresh();f.run();assertEquals(900,f.state().counts.notifications);assertEquals("NETWORK_ERROR",f.state().error);
    }
    @Test public void failedPageReloadCannotReuseInvalidatedOldPageForAcknowledgement() {
        Fixture f=new Fixture();f.open();InquiryData.NotificationPage original=f.state().page;f.actions.failLoad=true;f.vm.refresh();f.run();
        f.vm.acknowledge(original,"notice1");assertEquals(0,f.work.jobs.size());assertTrue(f.actions.acks.isEmpty());
    }
    @Test public void postAcknowledgementReloadFailureNeedsCanonicalPageBeforeAnotherWrite() {
        Fixture f=new Fixture();f.open();InquiryData.NotificationPage original=f.state().page;f.actions.failLoad=true;f.vm.acknowledge(original,"notice1");f.run();
        assertEquals(List.of("notice1"),f.actions.acks);f.vm.acknowledge(original,"notice1");assertEquals(0,f.work.jobs.size());
        f.actions.failLoad=false;f.vm.retry();f.run();assertNotNull(f.state().page.items.get(0).readAt);assertEquals(899,f.state().counts.notifications);
    }
    static InquiryData.Notification notice(String id,String type,String target,String read) {return new InquiryData.Notification(id,"actor",type,target,"event-"+id,TIME,read);}
    static final class Queue implements Executor {final ArrayDeque<Runnable> jobs=new ArrayDeque<>();public void execute(Runnable r){jobs.add(r);}void run(){jobs.remove().run();}}
    static final class Fake implements NotificationsViewModel.Actions {
        boolean current=true,failLoad,failAck,read;int loads;String afterTime,afterId;final List<String> acks=new ArrayList<>();
        public boolean allowed(){return true;}public void requireCurrent(){if(!current)throw new IllegalStateException("account changed");}
        public InquiryData.NotificationPage page(String time,String id)throws Exception{loads++;afterTime=time;afterId=id;if(failLoad)throw new java.io.IOException("lost");boolean second=id!=null;return new InquiryData.NotificationPage(List.of(notice(second?"notice2":"notice1","request",second?"request2":"request1",read?TIME:null)),second?null:TIME,second?null:"notice1");}
        public InquiryData.UnreadCounts counts(){return new InquiryData.UnreadCounts(read?899:900,80,Map.of());}
        public long acknowledge(InquiryData.NotificationPage page,List<String> ids)throws Exception{acks.addAll(ids);if(failAck)throw new java.io.IOException("lost");read=true;return 0;}
    }
    static final class Fixture {final Fake actions=new Fake();final Queue work=new Queue(),ui=new Queue();final NotificationsViewModel vm=new NotificationsViewModel(RuntimeEnvironment.getApplication(),actions,work,ui);NotificationsViewModel.State state(){return vm.state().getValue();}void run(){work.run();ui.run();}void open(){vm.ensureLoaded();run();}}
}
