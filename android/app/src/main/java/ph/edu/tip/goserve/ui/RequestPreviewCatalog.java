package ph.edu.tip.goserve.ui;

import android.content.Context;
import java.util.List;
import java.util.Arrays;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.domain.FeeDeclaration;
import ph.edu.tip.goserve.domain.PaymentStatus;
import ph.edu.tip.goserve.domain.RequestStatus;
import ph.edu.tip.goserve.domain.ServiceRequest;

public final class RequestPreviewCatalog {
    public static final String REQUIRED_ID = "demo-required";
    public static final String FREE_ID = "demo-free";
    public static final String PENDING_ID = "demo-pending";
    private RequestPreviewCatalog() {}

    public static List<ServiceRequest> examples(Context context) {
        return Arrays.asList(
                new ServiceRequest(REQUIRED_ID, context.getString(R.string.request_demo_required_title), "Registrar", "DEMO-001",
                        RequestStatus.PROCESSING, FeeDeclaration.REQUIRED, PaymentStatus.PAID, true),
                new ServiceRequest(FREE_ID, context.getString(R.string.request_demo_free_title), "Registrar", "DEMO-002",
                        RequestStatus.READY_FOR_PICKUP_DOWNLOAD, FeeDeclaration.FREE, null, true),
                new ServiceRequest(PENDING_ID, context.getString(R.string.request_demo_pending_title), "Guidance and Counseling Center", "DEMO-003",
                        RequestStatus.SUBMITTED, FeeDeclaration.PENDING_REVIEW, null, true));
    }

    public static ServiceRequest find(Context context, String id) {
        for (ServiceRequest request : examples(context)) if (request.id.equals(id)) return request;
        return null;
    }

    static int feeLabel(FeeDeclaration fee) {
        switch (fee) {
            case REQUIRED: return R.string.request_fee_required;
            case FREE: return R.string.request_fee_free;
            default: return R.string.request_fee_pending;
        }
    }
}
