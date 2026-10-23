package ph.edu.tip.goserve.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AccountValidation {
    public enum PasswordError {
        TOO_SHORT, LETTER_REQUIRED, DIGIT_REQUIRED, SYMBOL_REQUIRED, CONFIRMATION_MISMATCH
    }

    public enum ProfileError { REQUIRED, MIDDLE_NAME_CONFLICT }

    private AccountValidation() {}

    public static PasswordError passwordError(String password, String confirmation) {
        if (password == null || password.codePointCount(0, password.length()) < 6) return PasswordError.TOO_SHORT;
        boolean letter = false;
        boolean digit = false;
        boolean symbol = false;
        for (int offset = 0; offset < password.length();) {
            int codePoint = password.codePointAt(offset);
            letter |= Character.isLetter(codePoint);
            digit |= Character.getType(codePoint) == Character.DECIMAL_DIGIT_NUMBER;
            symbol |= isPunctuationOrSymbol(codePoint);
            offset += Character.charCount(codePoint);
        }
        if (!letter) return PasswordError.LETTER_REQUIRED;
        if (!digit) return PasswordError.DIGIT_REQUIRED;
        if (!symbol) return PasswordError.SYMBOL_REQUIRED;
        if (!password.equals(confirmation)) return PasswordError.CONFIRMATION_MISMATCH;
        return null;
    }

    public static boolean isValidEmail(String email) {
        if (email == null) return false;
        int separator = email.indexOf('@');
        if (separator <= 0 || separator != email.lastIndexOf('@')) return false;
        for (int offset = 0; offset < email.length();) {
            int codePoint = email.codePointAt(offset);
            if (isBackendWhitespace(codePoint)) return false;
            offset += Character.charCount(codePoint);
        }
        int dot = email.indexOf('.', separator + 2);
        return dot >= 0 && dot < email.length() - 1;
    }

    public static Map<String, ProfileError> profileErrors(StudentProfile profile) {
        Map<String, ProfileError> errors = new LinkedHashMap<>();
        required(errors, "first_name", profile.firstName);
        if (profile.noMiddleName) {
            if (profile.middleName != null && !profile.middleName.isEmpty()) {
                errors.put("middle_name", ProfileError.MIDDLE_NAME_CONFLICT);
            }
        } else required(errors, "middle_name", profile.middleName);
        required(errors, "last_name", profile.lastName);
        required(errors, "student_id", profile.studentId);
        required(errors, "course_program", profile.courseProgram);
        required(errors, "year_level", profile.yearLevel);
        required(errors, "mobile_number", profile.mobileNumber);
        required(errors, "gender", profile.gender);
        return Collections.unmodifiableMap(errors);
    }

    private static void required(Map<String, ProfileError> errors, String field, String value) {
        if (value == null || value.isEmpty()) {
            errors.put(field, ProfileError.REQUIRED);
            return;
        }
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            if (!isBackendWhitespace(codePoint)) return;
            offset += Character.charCount(codePoint);
        }
        errors.put(field, ProfileError.REQUIRED);
    }

    private static boolean isBackendWhitespace(int codePoint) {
        // Match ECMAScript trim/regex whitespace, including NBSP and BOM, without changing drafts.
        return codePoint >= 0x09 && codePoint <= 0x0D || codePoint == 0xFEFF || Character.isSpaceChar(codePoint);
    }

    private static boolean isPunctuationOrSymbol(int codePoint) {
        switch (Character.getType(codePoint)) {
            case Character.CONNECTOR_PUNCTUATION:
            case Character.DASH_PUNCTUATION:
            case Character.START_PUNCTUATION:
            case Character.END_PUNCTUATION:
            case Character.INITIAL_QUOTE_PUNCTUATION:
            case Character.FINAL_QUOTE_PUNCTUATION:
            case Character.OTHER_PUNCTUATION:
            case Character.MATH_SYMBOL:
            case Character.CURRENCY_SYMBOL:
            case Character.MODIFIER_SYMBOL:
            case Character.OTHER_SYMBOL:
                return true;
            default:
                return false;
        }
    }
}
