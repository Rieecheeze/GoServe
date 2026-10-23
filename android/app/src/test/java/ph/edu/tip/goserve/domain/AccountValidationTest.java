package ph.edu.tip.goserve.domain;

import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;

public final class AccountValidationTest {
    @Test public void passwordRequiresSixCodePoints() {
        assertNull(AccountValidation.passwordError("Ab1!xy", "Ab1!xy"));
        assertEquals(AccountValidation.PasswordError.TOO_SHORT,
                AccountValidation.passwordError("Ab1!x", "Ab1!x"));
    }

    @Test public void supplementaryCharactersCountOnce() {
        assertEquals(AccountValidation.PasswordError.TOO_SHORT,
                AccountValidation.passwordError("A1!\uD83D\uDE00x", "A1!\uD83D\uDE00x"));
        assertNull(AccountValidation.passwordError("A1!\uD83D\uDE00xy", "A1!\uD83D\uDE00xy"));
    }

    @Test public void unicodeLettersAndDecimalDigitsMeetTheRule() {
        assertNull(AccountValidation.passwordError("\u00C5\u00E9\u0661!xy", "\u00C5\u00E9\u0661!xy"));
        assertNull(AccountValidation.passwordError("\uD801\uDC00\u0661!xyz", "\uD801\uDC00\u0661!xyz"));
    }

    @Test public void letterDigitAndSymbolAreEachRequired() {
        assertEquals(AccountValidation.PasswordError.LETTER_REQUIRED,
                AccountValidation.passwordError("123!45", "123!45"));
        assertEquals(AccountValidation.PasswordError.DIGIT_REQUIRED,
                AccountValidation.passwordError("Abc!xy", "Abc!xy"));
        assertEquals(AccountValidation.PasswordError.SYMBOL_REQUIRED,
                AccountValidation.passwordError("Ab12xy", "Ab12xy"));
    }

    @Test public void nonDecimalNumbersDoNotReplaceADigit() {
        assertEquals(AccountValidation.PasswordError.DIGIT_REQUIRED,
                AccountValidation.passwordError("Ab\u2167!xy", "Ab\u2167!xy"));
    }

    @Test public void combiningMarksDoNotReplaceALetter() {
        assertEquals(AccountValidation.PasswordError.LETTER_REQUIRED,
                AccountValidation.passwordError("\u0301\u03011!23", "\u0301\u03011!23"));
    }

    @Test public void punctuationAndSymbolsSatisfyTheSymbolRequirement() {
        for (String password : new String[]{"Ab1_xy", "Ab1\u20ACxy", "Ab1\uD83D\uDE00xy"}) {
            assertNull(AccountValidation.passwordError(password, password));
        }
    }

    @Test public void whitespaceCountsTowardLengthWithoutBeingASymbol() {
        assertNull(AccountValidation.passwordError(" A1!xy", " A1!xy"));
        assertNull(AccountValidation.passwordError("A1!xy ", "A1!xy "));
        for (String password : new String[]{"Ab1 xy", "Ab1\txy", "Ab1\u00A0xy", "Ab1\u200Bxy"}) {
            assertEquals(AccountValidation.PasswordError.SYMBOL_REQUIRED,
                    AccountValidation.passwordError(password, password));
        }
    }

    @Test public void passwordIsNotNormalizedBeforeCounting() {
        assertNull(AccountValidation.passwordError("A\u03011!xy", "A\u03011!xy"));
    }

    @Test public void confirmationMatchesVerbatim() {
        for (String confirmation : new String[]{null, "", " Ab1!xy", "Ab1!xy ", "ab1!xy"}) {
            assertEquals(AccountValidation.PasswordError.CONFIRMATION_MISMATCH,
                    AccountValidation.passwordError("Ab1!xy", confirmation));
        }
        assertEquals(AccountValidation.PasswordError.CONFIRMATION_MISMATCH,
                AccountValidation.passwordError("A\u03011!xy", "\u00C11!xy"));
    }

    @Test public void missingPasswordIsInvalid() {
        assertEquals(AccountValidation.PasswordError.TOO_SHORT,
                AccountValidation.passwordError(null, null));
        assertEquals(AccountValidation.PasswordError.TOO_SHORT,
                AccountValidation.passwordError("", ""));
    }

    @Test public void registrationEmailIsNotRestrictedToGoogleDomain() {
        assertTrue(AccountValidation.isValidEmail("student1@demo.com"));
        assertTrue(AccountValidation.isValidEmail("student.name+study@example.edu"));
        assertTrue(AccountValidation.isValidEmail("\u00E9@\u4F8B\u3048.\u30C6\u30B9\u30C8"));
    }

    @Test public void malformedEmailAndSurroundingWhitespaceAreRejected() {
        for (String email : new String[]{null, "", " ", "student", "student@example", "@example.edu",
                "student@.edu", "student@example.", "student@@example.edu", "student name@example.edu",
                " student@example.edu", "student@example.edu ", "student\n@example.edu",
                "student\u00A0@example.edu", "\uFEFFstudent@example.edu"}) {
            assertFalse("Accepted malformed email", AccountValidation.isValidEmail(email));
        }
    }

    @Test public void completeProfileAcceptsFreeformStudentValues() {
        StudentProfile profile = new StudentProfile("Mar\u00EDa", "Dela", false, "Cruz",
                "visiting / X", "Emerging studies / studio", "Returning - year six",
                "local contact / extension 12", "Self-described");
        assertTrue(AccountValidation.profileErrors(profile).isEmpty());
    }

    @Test public void missingProfileValuesIdentifyTheirOwnFields() {
        String[] fields = {"first_name", "middle_name", "last_name", "student_id", "course_program",
                "year_level", "mobile_number", "gender"};
        for (int index = 0; index < fields.length; index++) {
            for (String missing : new String[]{null, "", " \t\n", "\u00A0", "\uFEFF"}) {
                String[] values = completeValues();
                values[index] = missing;
                Map<String, AccountValidation.ProfileError> errors = AccountValidation.profileErrors(profile(values, false));
                assertEquals(fields[index], 1, errors.size());
                assertEquals(fields[index], AccountValidation.ProfileError.REQUIRED, errors.get(fields[index]));
            }
        }
    }

    @Test public void noMiddleNameRequiresTheExplicitException() {
        for (String middle : new String[]{null, ""}) {
            String[] values = completeValues();
            values[1] = middle;
            assertTrue(AccountValidation.profileErrors(profile(values, true)).isEmpty());
            assertEquals(AccountValidation.ProfileError.REQUIRED,
                    AccountValidation.profileErrors(profile(values, false)).get("middle_name"));
        }
    }

    @Test public void noMiddleNameCannotCarryATextValue() {
        for (String middle : new String[]{"Dela", " ", "Not applicable"}) {
            String[] values = completeValues();
            values[1] = middle;
            Map<String, AccountValidation.ProfileError> errors = AccountValidation.profileErrors(profile(values, true));
            assertEquals(1, errors.size());
            assertEquals(AccountValidation.ProfileError.MIDDLE_NAME_CONFLICT, errors.get("middle_name"));
        }
    }

    @Test public void profileValidationPreservesEnteredDraftValues() {
        String[] values = completeValues();
        values[0] = "  Mar\u00EDa  ";
        values[4] = "  Open program  ";
        StudentProfile profile = profile(values, false);
        assertTrue(AccountValidation.profileErrors(profile).isEmpty());
        assertEquals("  Mar\u00EDa  ", profile.firstName);
        assertEquals("  Open program  ", profile.courseProgram);
    }

    private static String[] completeValues() {
        return new String[]{"Mar\u00EDa", "Dela", "Cruz", "student-X", "Open program", "Returning", "Contact", "Self-described"};
    }

    private static StudentProfile profile(String[] values, boolean noMiddleName) {
        return new StudentProfile(values[0], values[1], noMiddleName, values[2], values[3], values[4],
                values[5], values[6], values[7]);
    }
}
