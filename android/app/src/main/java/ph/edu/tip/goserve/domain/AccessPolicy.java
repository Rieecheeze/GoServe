package ph.edu.tip.goserve.domain;

import java.util.Locale;
import java.util.regex.Pattern;

public final class AccessPolicy {
    private static final Pattern LOCAL_PART = Pattern.compile(
            "[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*");

    private AccessPolicy() {}

    public static boolean isAllowedGoogleAccount(String email, boolean verified) {
        if (!verified || email == null || email.length() > 254) return false;
        int separator = email.indexOf('@');
        if (separator <= 0 || separator > 64 || separator != email.lastIndexOf('@')) return false;
        String localPart = email.substring(0, separator);
        String domain = email.substring(separator + 1);
        return LOCAL_PART.matcher(localPart).matches()
                && "tip.edu.ph".equals(domain.toLowerCase(Locale.ROOT));
    }
}
