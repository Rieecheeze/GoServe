package ph.edu.tip.goserve.ui;

import android.app.Application;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import ph.edu.tip.goserve.domain.Office;
import ph.edu.tip.goserve.domain.OfficeStatus;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34,application=Application.class)
public final class OfficeViewModelTest {
    @Test public void invalidationDuringReadQueuesOneCanonicalReread(){F f=new F();f.vm.reload(false);f.vm.reload(false);f.vm.reload(false);f.run();assertEquals(1,f.work.jobs.size());f.run();assertEquals(2,f.a.reads);}
    @Test public void loadingKeepsPreviouslyConfirmedInformationForTheSameAccount(){F f=loaded();f.vm.reload(false);assertTrue(f.s().loading);assertEquals("Registrar",f.s().offices.get(0).name);}
    @Test public void networkFailureRetainsOnlyCurrentAccountsLastConfirmedOfficeRows(){F f=loaded();f.a.fail=true;f.vm.reload(false);f.run();assertEquals("Registrar",f.s().offices.get(0).name);assertNotNull(f.s().error);}
    @Test public void oldAccountCannotPublishEitherSuccessOrFailure(){for(boolean error:new boolean[]{false,true}){F f=new F();f.a.fail=error;f.vm.reload(false);f.work.run();f.a.current=false;f.ui.run();assertTrue(f.s().offices.isEmpty());assertTrue(f.s().error.contains("account changed"));}}
    @Test public void explicitAccountInvalidationClearsCachedRows(){F f=loaded();f.a.current=false;f.vm.checkCurrentAccount();assertTrue(f.s().offices.isEmpty());assertTrue(f.s().error.contains("account changed"));}
    @Test public void previewUsesNoAuthenticationOrNetwork(){F f=new F();f.a.current=false;f.vm.reload(true);assertEquals(0,f.a.reads);assertEquals(0,f.a.checks);assertEquals("Registrar",f.s().offices.get(0).name);}
    static F loaded(){F f=new F();f.vm.reload(false);f.run();return f;}
    static Office office(OfficeStatus status,String note){return new Office("OFF-REG","Registrar","Actual campus",status,note,"Documents",true);}
    static final class F{final A a=new A();final OfficeManagementViewModelTest.Q work=new OfficeManagementViewModelTest.Q(),ui=new OfficeManagementViewModelTest.Q();final OfficeViewModel vm=new OfficeViewModel(RuntimeEnvironment.getApplication(),a,work,ui);OfficeViewModel.State s(){return vm.state().getValue();}void run(){work.run();ui.run();}}
    static final class A implements OfficeViewModel.Actions{boolean current=true,fail;int reads,checks;List<Office> rows=List.of(office(OfficeStatus.OPEN,"Actual note"));public void requireCurrent(){checks++;if(!current)throw new IllegalStateException("account changed");}public List<Office> load()throws Exception{reads++;if(fail)throw new java.io.IOException();return rows;}public List<Office> preview(){return rows;}}
}
