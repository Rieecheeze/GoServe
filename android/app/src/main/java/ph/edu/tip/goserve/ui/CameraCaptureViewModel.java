package ph.edu.tip.goserve.ui;

import android.app.Application;
import android.content.Intent;
import androidx.lifecycle.AndroidViewModel;
import ph.edu.tip.goserve.data.CameraEvidence;

public final class CameraCaptureViewModel extends AndroidViewModel {
    private CameraEvidence capture;
    private boolean pending;
    public CameraCaptureViewModel(Application app) { super(app); }
    CameraEvidence begin() throws Exception {
        if(pending)return capture;
        clear(); capture=CameraEvidence.prepare(getApplication()); pending=true; return capture;
    }
    CameraEvidence current() { return capture; }
    boolean pending() { return pending; }
    void returned() { pending=false; }
    void clear() {
        pending=false;
        if(capture!=null) {
            getApplication().revokeUriPermission(capture.uri(),Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            capture.close(); capture=null;
        }
    }
    @Override protected void onCleared() { clear(); super.onCleared(); }
}
