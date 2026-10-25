package ph.edu.tip.goserve.data;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public final class AuthEmailErrorTest {
    @Test public void authErrorCodeSurvivesWithoutPrivateProviderDetails() {
        for(String code:new String[]{"invalid_credentials","current_password_mismatch","reauthentication_needed","reauthentication_not_valid","same_password","weak_password","otp_expired"}) {
            SupabaseClient.ApiException error=SupabaseClient.apiError(422,"{\"code\":\""+code+"\",\"msg\":\"private email and secret\"}");
            assertEquals(code,error.code); assertFalse(error.getMessage().contains("private"));
        }
        assertEquals("invalid_credentials",SupabaseClient.apiError(400,"{\"code\":400,\"error_code\":\"invalid_credentials\",\"msg\":\"private\"}").code);
    }
    @Test public void undeliverableAndRejectedEmailsHaveActionableSanitizedMessages() {
        SupabaseClient.ApiException demo=SupabaseClient.apiError(409,"{\"error\":{\"code\":\"EMAIL_UNAVAILABLE\"}}");
        assertEquals("EMAIL_UNAVAILABLE",demo.code); assertTrue(demo.getMessage().contains("cannot receive email"));
        SupabaseClient.ApiException smtp=SupabaseClient.apiError(503,"{\"error\":{\"code\":\"EMAIL_DELIVERY_FAILED\"},\"message\":\"private provider detail\"}");
        assertEquals("EMAIL_DELIVERY_FAILED",smtp.code); assertTrue(smtp.getMessage().contains("email")); assertFalse(smtp.getMessage().contains("private"));
    }
}
