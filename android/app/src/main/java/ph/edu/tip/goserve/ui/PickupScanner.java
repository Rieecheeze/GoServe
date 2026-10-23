package ph.edu.tip.goserve.ui;

import android.app.Activity;

interface PickupScanner {
    interface Result { void found(String code); void cancelled(); void unavailable(); }
    void scan(Activity activity,Result result);
}
