package ph.edu.tip.goserve.domain;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Runs the domain contract without an Android SDK or test library. */
public final class DomainTestRunner {
    private static int assertions;

    private DomainTestRunner() {}

    public static void main(String[] args) {
        runAll();
        System.out.println("PASS: " + assertions + " GoServe domain assertions");
    }

    public static void runAll() {
        assertions = 0;
        wireContracts();
        unknownWireValuesFailClosed();
        terminalRequestStates();
        googleAccountAccess();
        immutableProfileContract();
        immutableOfficeContract();
    }

    private static void wireContracts() {
        assertWire("Role", "STUDENT", "student");
        assertWire("Role", "STAFF", "staff");
        assertWire("Role", "TELLER", "teller");
        assertWire("OfficeStatus", "OPEN", "Open");
        assertWire("OfficeStatus", "ON_LUNCH_BREAK", "On Lunch Break");
        assertWire("OfficeStatus", "CUT_OFF_REACHED", "Cut-Off Reached");
        assertWire("OfficeStatus", "TEMPORARILY_CLOSED", "Temporarily Closed");
        assertWire("RequestStatus", "SUBMITTED", "Submitted");
        assertWire("RequestStatus", "AWAITING_PAYMENT", "Awaiting Payment");
        assertWire("RequestStatus", "PROCESSING", "Processing");
        assertWire("RequestStatus", "READY_FOR_PICKUP_DOWNLOAD", "Ready for Pickup/Download");
        assertWire("RequestStatus", "COMPLETED", "Completed");
        assertWire("RequestStatus", "CANCELLED", "Cancelled");
        assertWire("RequestStatus", "REJECTED", "Rejected");
        assertWire("PaymentStatus", "PENDING", "pending");
        assertWire("PaymentStatus", "PAID", "paid");
        assertWire("PaymentStatus", "FAILED", "failed");
        assertWire("PaymentStatus", "EXPIRED", "expired");
        assertWire("PaymentStatus", "CANCELLED", "cancelled");
        assertWire("PaymentMethod", "EGOVPAY", "egovpay");
        assertWire("PaymentMethod", "CASH", "cash");
    }

    private static void unknownWireValuesFailClosed() {
        for (String type : new String[]{"Role", "OfficeStatus", "RequestStatus", "PaymentStatus", "PaymentMethod"}) {
            for (String value : new String[]{null, "", "unknown", " "}) {
                expectIllegalArgument(() -> fromWire(type, value), type + " accepts unknown value " + value);
            }
        }
        expectIllegalArgument(() -> fromWire("Role", "Student"), "Role silently changes wire case");
        expectIllegalArgument(() -> fromWire("Role", " student "), "Role silently trims wire value");
        expectIllegalArgument(() -> fromWire("OfficeStatus", "open"), "Office status silently changes wire case");
        expectIllegalArgument(() -> fromWire("RequestStatus", "paid"), "Payment status is accepted as a request status");
        expectIllegalArgument(() -> fromWire("PaymentStatus", "Completed"), "Request status is accepted as a payment status");
        expectIllegalArgument(() -> fromWire("PaymentMethod", "card"), "Unsupported payment method is accepted");
    }

    private static void terminalRequestStates() {
        for (String value : new String[]{"Submitted", "Awaiting Payment", "Processing", "Ready for Pickup/Download"}) {
            check(!((Boolean) invoke(fromWire("RequestStatus", value), "isTerminal")), value + " prematurely ends a request");
        }
        for (String value : new String[]{"Completed", "Cancelled", "Rejected"}) {
            check((Boolean) invoke(fromWire("RequestStatus", value), "isTerminal"), value + " does not end a request");
        }
    }

    private static void googleAccountAccess() {
        check(isAllowed("student@tip.edu.ph", true), "Verified TIP student cannot sign in");
        check(isAllowed("Student.Name+campus@TIP.EDU.PH", true), "TIP domain comparison must be case insensitive");
        check(!isAllowed("student@tip.edu.ph", false), "Unverified account is allowed");
        for (String email : new String[]{
                null, "", " ", "@tip.edu.ph", "studenttip.edu.ph", "student@@tip.edu.ph",
                "student@tip.edu.ph.attacker.example", "student@attacker-tip.edu.ph", "student@sub.tip.edu.ph",
                "student@tip.edu.ph@attacker.example", "student@tip.edu.ph.", "student@tip.edu.ph ",
                " student@tip.edu.ph", "stu dent@tip.edu.ph", "student\n@tip.edu.ph",
                ".student@tip.edu.ph", "student.@tip.edu.ph", "stu..dent@tip.edu.ph",
                "student<>@tip.edu.ph", "student@t\u0130p.edu.ph", "Student <student@tip.edu.ph>"}) {
            check(!isAllowed(email, true), "Malformed or non-TIP account is allowed: " + email);
        }
    }

    private static void immutableProfileContract() {
        Class<?> type = domainClass("SessionProfile");
        Object profile = construct(type,
                new Class<?>[]{String.class, String.class, String.class, domainClass("Role"), String.class, String.class, boolean.class, boolean.class},
                "user-1", "student@tip.edu.ph", "Student Name", fromWire("Role", "student"), null, null, false, true);
        equal("user-1", field(profile, "id"), "Profile ID is changed");
        equal("student@tip.edu.ph", field(profile, "email"), "Profile email is changed");
        equal("Student Name", field(profile, "displayName"), "Profile display name is changed");
        equal(fromWire("Role", "student"), field(profile, "role"), "Profile role is changed");
        equal(null, field(profile, "officeId"), "Student receives an office scope");
        equal(null, field(profile, "capability"), "Student receives a capability");
        equal(false, field(profile, "demo"), "Production profile becomes a demo");
        equal(true, field(profile, "consentAccepted"), "Accepted consent is lost");
        assertImmutableFields(type, "id", "email", "displayName", "role", "officeId", "capability", "demo", "consentAccepted");
    }

    private static void immutableOfficeContract() {
        Class<?> type = domainClass("Office");
        Object office = construct(type,
                new Class<?>[]{String.class, String.class, String.class, domainClass("OfficeStatus"), String.class, String.class, boolean.class},
                "registrar-casal", "Registrar", "Casal", fromWire("OfficeStatus", "Open"), "Demo data", "Student services", true);
        equal("registrar-casal", field(office, "id"), "Office ID is changed");
        equal("Registrar", field(office, "name"), "Office name is changed");
        equal("Casal", field(office, "campus"), "Office campus is changed");
        equal(fromWire("OfficeStatus", "Open"), field(office, "status"), "Office status is changed");
        equal("Demo data", field(office, "note"), "Office note is changed");
        equal("Student services", field(office, "category"), "Office category is changed");
        equal(true, field(office, "demo"), "Demo office is presented as official");
        assertImmutableFields(type, "id", "name", "campus", "status", "note", "category", "demo");
    }

    private static void assertWire(String type, String enumName, String wire) {
        Object parsed = fromWire(type, wire);
        equal(enumName, ((Enum<?>) parsed).name(), type + " maps to the wrong enum");
        equal(wire, field(parsed, "wireValue"), type + " changes the server value");
    }

    private static Object fromWire(String type, String wire) {
        return invokeStatic(domainClass(type), "fromWire", new Class<?>[]{String.class}, (Object) wire);
    }

    private static boolean isAllowed(String email, boolean verified) {
        return (Boolean) invokeStatic(domainClass("AccessPolicy"), "isAllowedGoogleAccount",
                new Class<?>[]{String.class, boolean.class}, email, verified);
    }

    private static Class<?> domainClass(String name) {
        try {
            return Class.forName("ph.edu.tip.goserve.domain." + name);
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("Required GoServe domain API is missing: " + name, exception);
        }
    }

    private static Object invoke(Object target, String name) {
        return invokeMethod(target.getClass(), target, name, new Class<?>[0]);
    }

    private static Object invokeStatic(Class<?> type, String name, Class<?>[] parameterTypes, Object... args) {
        return invokeMethod(type, null, name, parameterTypes, args);
    }

    private static Object invokeMethod(Class<?> type, Object target, String name, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = type.getMethod(name, parameterTypes);
            return method.invoke(target, args);
        } catch (InvocationTargetException exception) {
            throwCause(exception);
            return null;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Required domain method is missing: " + type.getSimpleName() + "." + name, exception);
        }
    }

    private static Object construct(Class<?> type, Class<?>[] parameterTypes, Object... args) {
        try {
            Constructor<?> constructor = type.getConstructor(parameterTypes);
            return constructor.newInstance(args);
        } catch (InvocationTargetException exception) {
            throwCause(exception);
            return null;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Required constructor is missing: " + type.getSimpleName(), exception);
        }
    }

    private static void throwCause(InvocationTargetException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof RuntimeException) throw (RuntimeException) cause;
        if (cause instanceof Error) throw (Error) cause;
        throw new AssertionError("Unexpected checked exception", cause);
    }

    private static Object field(Object value, String name) {
        try {
            return value.getClass().getField(name).get(value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Required public domain field is missing: " + name, exception);
        }
    }

    private static void assertImmutableFields(Class<?> type, String... names) {
        check(Modifier.isFinal(type.getModifiers()), type.getSimpleName() + " can be extended and mutated");
        for (String name : names) {
            try {
                check(Modifier.isFinal(type.getField(name).getModifiers()), name + " is mutable");
            } catch (NoSuchFieldException exception) {
                throw new AssertionError("Required immutable field is missing: " + name, exception);
            }
        }
    }

    private static void expectIllegalArgument(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            assertions++;
            return;
        }
        throw new AssertionError(message);
    }

    private static void equal(Object expected, Object actual, String message) {
        check(expected == null ? actual == null : expected.equals(actual), message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
