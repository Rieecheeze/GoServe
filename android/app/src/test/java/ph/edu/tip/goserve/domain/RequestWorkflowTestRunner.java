package ph.edu.tip.goserve.domain;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

public final class RequestWorkflowTestRunner {
    private static int assertions;
    private RequestWorkflowTestRunner() {}

    public static void main(String[] args) {
        runAll();
        System.out.println("PASS: " + assertions + " request workflow assertions");
    }

    public static void runAll() {
        assertions = 0;
        Object required = fee("REQUIRED"), free = fee("FREE"), pending = fee("PENDING_REVIEW");
        check(required.equals(invokeFromWire("required")), "Required fee wire value is not preserved.");
        check(free.equals(invokeFromWire("free")), "Free fee wire value is not preserved.");
        check(pending.equals(invokeFromWire("pending_review")), "Pending fee wire value is not preserved.");
        check(status("AWAITING_PAYMENT").equals(call("afterFeeDeclaration", required)), "Required fee must await payment.");
        check(status("SUBMITTED").equals(call("afterFeeDeclaration", free)), "Free request must skip Awaiting Payment.");
        check(status("SUBMITTED").equals(call("afterFeeDeclaration", pending)), "Unknown fee must await an office decision.");
        for (String payment : new String[]{"PENDING", "FAILED", "EXPIRED", "CANCELLED"}) {
            check(!allowed(required, payment), "Required request advances with " + payment);
            check(allowed(free, payment), "An unrelated " + payment + " payment blocks a free request.");
            check(!allowed(pending, payment), "Pending fee decision advances with " + payment);
        }
        check(allowed(required, "PAID"), "A paid required request cannot process.");
        check(!allowed(required, null), "Missing payment authorizes a required request.");
        check(allowed(free, null), "Free request needs a payment record.");
        check(!allowed(pending, "PAID"), "Payment substitutes for an office fee decision.");
        check(!allowed(pending, null), "Missing fee decision permits processing.");
        for (Object declaration : new Object[]{required, free, pending}) {
            List<?> phases = (List<?>) call("phases", declaration);
            check(phases.get(0).equals(status("SUBMITTED")), "Request must begin with Submitted.");
            check(phases.get(phases.size() - 1).equals(status("COMPLETED")), "Request must end with Completed.");
            check(phases.contains(status("AWAITING_PAYMENT")) == declaration.equals(required), "Payment phase does not follow the fee declaration.");
        }
        check("DONE".equals(phase(required, "PROCESSING", "SUBMITTED")), "Submitted must be visibly complete.");
        check("DONE".equals(phase(required, "PROCESSING", "AWAITING_PAYMENT")), "Paid phase must be visibly complete.");
        check("CURRENT".equals(phase(required, "PROCESSING", "PROCESSING")), "Processing must be current.");
        check("UPCOMING".equals(phase(required, "PROCESSING", "COMPLETED")), "Completed must not be fabricated.");
        for (Object step : (List<?>) call("phases", free)) {
            check("DONE".equals(call("phaseState", free, status("COMPLETED"), step).toString()), "Completed request has an incomplete phase.");
        }
        check("DONE".equals(phase(free, "READY_FOR_PICKUP_DOWNLOAD", "PROCESSING")), "A ready free request must have completed Processing.");
        check("CURRENT".equals(phase(free, "READY_FOR_PICKUP_DOWNLOAD", "READY_FOR_PICKUP_DOWNLOAD")), "Ready phase must be current.");
        check("UPCOMING".equals(phase(free, "REJECTED", "PROCESSING")), "Rejected request fabricates completed processing.");
        reject(() -> call("phaseState", free, status("AWAITING_PAYMENT"), status("SUBMITTED")), "Free request accepts a payment phase.");
        reject(() -> call("phaseState", pending, status("PROCESSING"), status("SUBMITTED")), "Undeclared fee displays processing.");
        for (String next : new String[]{"PROCESSING", "READY_FOR_PICKUP_DOWNLOAD", "COMPLETED"}) {
            check(!(Boolean) call("canAdvanceTo", required, null, status(next)), "Missing payment permits " + next);
            check((Boolean) call("canAdvanceTo", free, null, status(next)), "Free request cannot reach " + next);
            check(!(Boolean) call("canAdvanceTo", pending, enumValue("PaymentStatus", "PAID"), status(next)), "Unknown declaration permits " + next);
        }
        check(!(Boolean) call("canAdvanceTo", free, null, status("AWAITING_PAYMENT")), "Free request can enter Awaiting Payment.");
        check((Boolean) call("canAdvanceTo", required, null, status("CANCELLED")), "Unpaid request cannot be cancelled.");
        reject(() -> call("afterFeeDeclaration", (Object) null), "Null fee becomes a silent default.");
        for (String value : new String[]{null, "", "unknown", "Required", " required "}) {
            reject(() -> invokeFromWire(value), "Unknown fee declaration is accepted: " + value);
        }
        ServiceRequest freeReady = new ServiceRequest("free", "Document", "Office", "DEMO", RequestStatus.READY_FOR_PICKUP_DOWNLOAD,
                FeeDeclaration.FREE, null, true);
        check(freeReady.paymentStatus == null, "A free request fabricates a payment record.");
        check(freeReady.status == RequestStatus.READY_FOR_PICKUP_DOWNLOAD, "A free request loses the office request status.");
        reject(() -> new ServiceRequest("required", "Document", "Office", "DEMO", RequestStatus.PROCESSING,
                FeeDeclaration.REQUIRED, PaymentStatus.PENDING, true), "Request model accepts unpaid processing.");
        reject(() -> new ServiceRequest("free", "Document", "Office", "DEMO", RequestStatus.AWAITING_PAYMENT,
                FeeDeclaration.FREE, null, true), "Request model inserts Awaiting Payment for a free service.");
        reject(() -> new ServiceRequest("unknown", "Document", "Office", "DEMO", RequestStatus.COMPLETED,
                FeeDeclaration.PENDING_REVIEW, PaymentStatus.PAID, true), "Payment invents the office fee declaration.");
        reject(() -> new ServiceRequest("", "Document", "Office", "DEMO", RequestStatus.SUBMITTED,
                FeeDeclaration.FREE, null, true), "Request model accepts an empty identity.");
    }

    private static Object invokeFromWire(String value) { return invoke(type("FeeDeclaration"), "fromWire", value); }
    private static Object fee(String name) { return enumValue("FeeDeclaration", name); }
    private static Object status(String name) { return enumValue("RequestStatus", name); }
    private static boolean allowed(Object fee, String payment) { return (Boolean) call("canStartProcessing", fee, payment == null ? null : enumValue("PaymentStatus", payment)); }
    private static String phase(Object fee, String current, String step) { return call("phaseState", fee, status(current), status(step)).toString(); }
    private static Object call(String method, Object... args) { return invoke(type("RequestWorkflow"), method, args); }
    private static Object enumValue(String typeName, String name) {
        for (Object value : type(typeName).getEnumConstants()) if (((Enum<?>) value).name().equals(name)) return value;
        throw new AssertionError("Missing " + typeName + "." + name);
    }
    private static Class<?> type(String name) {
        try { return Class.forName("ph.edu.tip.goserve.domain." + name); }
        catch (ClassNotFoundException missing) { throw new AssertionError("Missing request contract: " + name, missing); }
    }
    private static Object invoke(Class<?> owner, String name, Object... args) {
        for (java.lang.reflect.Method method : owner.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            try { return method.invoke(null, args); }
            catch (InvocationTargetException error) {
                if (error.getCause() instanceof IllegalArgumentException) throw (IllegalArgumentException) error.getCause();
                throw new AssertionError(error.getCause());
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        }
        throw new AssertionError("Missing request method: " + name);
    }
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    private static void reject(Runnable action, String message) {
        assertions++;
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(message);
    }
}
