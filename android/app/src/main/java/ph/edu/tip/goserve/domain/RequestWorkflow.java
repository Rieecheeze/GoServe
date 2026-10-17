package ph.edu.tip.goserve.domain;

import java.util.List;

public final class RequestWorkflow {
    public enum PhaseState { DONE, CURRENT, UPCOMING }
    private RequestWorkflow() {}

    public static RequestStatus afterFeeDeclaration(FeeDeclaration fee) {
        requireFee(fee);
        return fee == FeeDeclaration.REQUIRED ? RequestStatus.AWAITING_PAYMENT : RequestStatus.SUBMITTED;
    }

    public static boolean canStartProcessing(FeeDeclaration fee, PaymentStatus payment) {
        requireFee(fee);
        return fee == FeeDeclaration.FREE || fee == FeeDeclaration.REQUIRED && payment == PaymentStatus.PAID;
    }

    public static boolean canAdvanceTo(FeeDeclaration fee, PaymentStatus payment, RequestStatus next) {
        requireFee(fee);
        if (next == null) throw new IllegalArgumentException("Request status is required");
        switch (next) {
            case AWAITING_PAYMENT: return fee == FeeDeclaration.REQUIRED;
            case PROCESSING:
            case READY_FOR_PICKUP_DOWNLOAD:
            case COMPLETED: return canStartProcessing(fee, payment);
            default: return true;
        }
    }

    public static List<RequestStatus> phases(FeeDeclaration fee) {
        requireFee(fee);
        if (fee == FeeDeclaration.REQUIRED) return List.of(RequestStatus.SUBMITTED, RequestStatus.AWAITING_PAYMENT,
                RequestStatus.PROCESSING, RequestStatus.READY_FOR_PICKUP_DOWNLOAD, RequestStatus.COMPLETED);
        return List.of(RequestStatus.SUBMITTED, RequestStatus.PROCESSING,
                RequestStatus.READY_FOR_PICKUP_DOWNLOAD, RequestStatus.COMPLETED);
    }

    public static PhaseState phaseState(FeeDeclaration fee, RequestStatus current, RequestStatus phase) {
        List<RequestStatus> steps = phases(fee);
        if (current == null || !steps.contains(phase)) throw new IllegalArgumentException("Unknown request phase");
        if (fee == FeeDeclaration.PENDING_REVIEW && current != RequestStatus.SUBMITTED && !current.isTerminal()) {
            throw new IllegalArgumentException("An office fee decision is required before processing");
        }
        if (fee == FeeDeclaration.PENDING_REVIEW && current == RequestStatus.COMPLETED) {
            throw new IllegalArgumentException("An office fee decision is required before completion");
        }
        if (current == RequestStatus.CANCELLED || current == RequestStatus.REJECTED) {
            return phase == RequestStatus.SUBMITTED ? PhaseState.DONE : PhaseState.UPCOMING;
        }
        int currentIndex = steps.indexOf(current);
        if (currentIndex < 0) throw new IllegalArgumentException("Request status does not match its office fee declaration");
        int phaseIndex = steps.indexOf(phase);
        if (current == RequestStatus.COMPLETED || phaseIndex < currentIndex) return PhaseState.DONE;
        return phaseIndex == currentIndex ? PhaseState.CURRENT : PhaseState.UPCOMING;
    }

    private static void requireFee(FeeDeclaration fee) {
        if (fee == null) throw new IllegalArgumentException("Office fee declaration is required");
    }
}
