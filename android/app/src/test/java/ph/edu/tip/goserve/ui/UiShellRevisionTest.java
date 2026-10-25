package ph.edu.tip.goserve.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Looper;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.domain.TermsDocument;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=GoServeApplication.class,qualifiers="w390dp-h760dp-port-notnight-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public final class UiShellRevisionTest {
    @Test public void moreGroupsAccountAndSeryoActionsBeforeAppearance() {
        Context app=RuntimeEnvironment.getApplication();
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class,new Intent(app,MainActivity.class).putExtra(MainActivity.EXTRA_PREVIEW,true)).setup().visible()) {
            MainActivity activity=controller.get();
            activity.navigate(new MoreFragment(),false);activity.getSupportFragmentManager().executePendingTransactions();
            View root=activity.getSupportFragmentManager().findFragmentById(R.id.mainContainer).requireView();
            assertNull("Profile actions belong inside Account.",root.findViewById(R.id.profilePhotoButton));
            assertNotNull(button(root,"Account"));assertNotNull(button(root,"Seryo AI"));assertNotNull(button(root,"Terms And Conditions"));assertNotNull(button(root,"About"));
            assertTrue(button(root,"Account").performClick());activity.getSupportFragmentManager().executePendingTransactions();
            assertNotNull(activity.findViewById(R.id.profilePhotoButton));assertNotNull(activity.findViewById(R.id.changePasswordButton));
        }
    }

    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void normalPhoneShowsEverySignInActionAndCompactRecoveryLinkWithoutScrolling() {
        Context context=new ContextThemeWrapper(RuntimeEnvironment.getApplication(),R.style.Theme_GoServe);
        NestedScrollView root=(NestedScrollView)LayoutInflater.from(context).inflate(R.layout.fragment_sign_in,null,false);
        root.findViewById(R.id.previewButton).setVisibility(View.VISIBLE);
        root.measure(View.MeasureSpec.makeMeasureSpec(390,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(760,View.MeasureSpec.EXACTLY));
        root.layout(0,0,390,760);
        assertTrue("All normal sign-in actions must fit a standard phone. "+geometry(root.findViewById(R.id.accountCard)),root.getChildAt(0).getHeight()<=root.getHeight());
        View recovery=root.findViewById(R.id.forgotPasswordButton),password=root.findViewById(R.id.passwordBox);
        assertTrue("Recovery belongs directly below the password.",recovery.getTop()-password.getBottom()<=8);
        assertTrue("Recovery remains a 48dp touch target.",recovery.getHeight()>=48);
        assertEquals(0f,root.findViewById(R.id.accountCard).getElevation(),0f);
    }

    @Test public void approvedTermsRenderHeadingsBoldWithoutChangingTheSignedDocument() {
        String approved="# GoServe Test Terms\n\nVersion: approved-v1\n\n## 1. Account Information\n\nBody text remains regular. Keep reference #123 unchanged.";
        TermsDocument document=new TermsDocument("approved-v1",approved,"approved-digest","published-at");
        Context app=RuntimeEnvironment.getApplication();
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class,new Intent(app,MainActivity.class).putExtra(MainActivity.EXTRA_PREVIEW,true)).setup().visible()) {
            MainActivity activity=controller.get();
            AuthViewModel model=new ViewModelProvider(activity).get(AuthViewModel.class);
            ReflectionHelpers.setField(model,"terms",document);
            androidx.lifecycle.MutableLiveData<AuthViewModel.State> state=ReflectionHelpers.getField(model,"state");
            state.setValue(new AuthViewModel.State(AuthViewModel.Route.TERMS,false,null,null,document,java.util.Collections.emptyMap(),null));
            activity.navigate(TermsFragment.readOnly(),true);activity.getSupportFragmentManager().executePendingTransactions();Shadows.shadowOf(Looper.getMainLooper()).idle();
            TextView content=activity.findViewById(R.id.termsContent);
            assertEquals("GoServe Test Terms\n\nVersion: approved-v1\n\n1. Account Information\n\nBody text remains regular. Keep reference #123 unchanged.",content.getText().toString());
            assertTrue(content.getText() instanceof Spanned);
            Spanned rendered=(Spanned)content.getText();
            StyleSpan[] spans=rendered.getSpans(0,rendered.length(),StyleSpan.class);
            assertEquals(2,spans.length);
            for(StyleSpan span:spans){assertEquals(Typeface.BOLD,span.getStyle());assertTrue(rendered.getSpanEnd(span)<=rendered.toString().indexOf("Body text"));}
            assertSame(document,model.state().getValue().terms);assertEquals(approved,document.content);assertEquals("approved-digest",document.contentDigest);
        }
    }
    private MaterialButton button(View view,String label) {
        if(view instanceof MaterialButton && label.contentEquals(((MaterialButton)view).getText()))return (MaterialButton)view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int index=0;index<group.getChildCount();index++){MaterialButton found=button(group.getChildAt(index),label);if(found!=null)return found;}}
        return null;
    }
    private String geometry(View card){StringBuilder result=new StringBuilder("cardHeight=").append(card.getHeight());ViewGroup group=(ViewGroup)card;for(int index=0;index<group.getChildCount();index++){View item=group.getChildAt(index);if(item.getVisibility()==View.VISIBLE)result.append(" ").append(item.getId()==View.NO_ID?item.getClass().getSimpleName():item.getResources().getResourceEntryName(item.getId())).append(":").append(item.getTop()).append("+").append(item.getHeight());}return result.toString();}
}
