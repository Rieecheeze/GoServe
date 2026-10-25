package ph.edu.tip.goserve.data;

import android.net.Uri;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,shadows={AccountIntegrationTest.Store.class,AccountIntegrationTest.Server.class},instrumentedPackages="ph.edu.tip.goserve.data")
public final class RecoveryClientRoutingTest {
    private AuthRepository auth;
    @Before public void setup(){new AccountIntegrationTest().setup();auth=new AuthRepository(new SupabaseClient("https://example.supabase.co","fixture"),new SecureSessionStore(RuntimeEnvironment.getApplication()));}
    @Test public void publicEmailRegistrationIsRetiredWithoutSendingCredentials() throws Exception {
        int before=AccountIntegrationTest.Server.calls;
        assertThrows(IllegalStateException.class,()->auth.registerStudent("ana@tip.edu.ph","Valid 2!",new ph.edu.tip.goserve.domain.StudentProfile("Ana","",true,"Cruz","ID","Program","Year","Mobile","Gender"),"v1","884cd499-a440-48ef-a6ef-b8ddfe1bc304"));
        assertEquals(before,AccountIntegrationTest.Server.calls);assertNull(auth.profile());
    }
    @Test public void emailRecoveryRequestNeverContactsAnSmtpEndpointOrPersistsAVerifier() {
        int before=AccountIntegrationTest.Server.calls;
        assertThrows(IllegalStateException.class,()->auth.requestRecovery("ana@tip.edu.ph"));
        assertEquals(before,AccountIntegrationTest.Server.calls);assertFalse(AccountIntegrationTest.Store.values.containsKey("recovery_pending"));
    }
    @Test public void emailedRecoveryCallbackCannotExchangeAnAuthCode() {
        int before=AccountIntegrationTest.Server.calls;
        assertThrows(IllegalStateException.class,()->auth.finishRecovery(Uri.parse("goserve://auth/recovery?code=old-email-code&flow=recovery")));
        assertEquals(before,AccountIntegrationTest.Server.calls);assertNull(auth.profile());
    }
    @Test public void oldPasswordChangeCannotBypassTheRecoveryGrantFlow() throws Exception {
        auth.signInPassword("ana@example.com","Existing 1!");AuthRepository.SessionLease lease=auth.sessionLease();int before=AccountIntegrationTest.Server.calls;
        assertThrows(IllegalStateException.class,()->auth.requestPasswordReauthentication(lease));
        assertThrows(IllegalStateException.class,()->auth.changePassword(lease,"old password","Valid 2!","Valid 2!","123456"));
        assertThrows(IllegalStateException.class,()->auth.resetPassword("Valid 2!","Valid 2!"));
        assertEquals(before,AccountIntegrationTest.Server.calls);assertNotNull(auth.profile());
    }
}
