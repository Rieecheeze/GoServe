package ph.edu.tip.goserve.ui;

import android.app.Application;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.data.AuthRepository;
import ph.edu.tip.goserve.data.SupabaseClient;
import ph.edu.tip.goserve.domain.AccountValidation;
import ph.edu.tip.goserve.domain.SessionProfile;
import ph.edu.tip.goserve.domain.StudentProfile;
import ph.edu.tip.goserve.domain.TermsDocument;

public final class AuthViewModel extends AndroidViewModel {
    public enum Route { RESTORING, SIGN_IN, TERMS, CREDENTIALS, PROFILE, ACCOUNT_CREATED, RESET_EMAIL, RESET_SENT, RESET_PASSWORD, RESET_COMPLETE, RESET_UNAVAILABLE, SESSION_RETRY, HOME }
    private enum TermsMode { REGISTER, ACCOUNT, READ_ONLY }
    public static final class Draft {
        public String email="",recoveryEmail="";
        public StudentProfile profile=new StudentProfile("","",false,"","","","","","");
    }
    public static final class State {
        public final Route route;
        public final boolean loading;
        public final String error;
        public final SessionProfile profile;
        public final TermsDocument terms;
        public final Map<String,String> fieldErrors;
        public final AuthRepository.PasswordChangeNotice passwordNotice;
        State(Route route,boolean loading,String error,SessionProfile profile,TermsDocument terms,Map<String,String> errors,AuthRepository.PasswordChangeNotice notice) {
            this.route=route; this.loading=loading; this.error=error; this.profile=profile; this.terms=terms;
            this.passwordNotice=notice;
            this.fieldErrors=Collections.unmodifiableMap(new LinkedHashMap<>(errors));
        }
    }
    public static final class OneTime<T> {
        private final T value; private boolean consumed;
        OneTime(T value) { this.value=value; }
        public synchronized T consume() { if (consumed) return null; consumed=true; return value; }
    }
    private final GoServeApplication app;
    private final AuthRepository auth;
    private final SavedStateHandle saved;
    private final Draft draft=new Draft();
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final LinkedHashSet<Uri> callbacks=new LinkedHashSet<>();
    private final MutableLiveData<State> state=new MutableLiveData<>();
    private final MutableLiveData<OneTime<Uri>> googleUrl=new MutableLiveData<>();
    private TermsDocument terms;
    private TermsMode termsMode=TermsMode.READ_ONLY;
    private Route termsReturn=Route.SIGN_IN;
    private String acceptedTerms,registrationPassword="",registrationConfirmation="",signinPassword="",resetPassword="",resetConfirmation="";
    private String registrationKey,updateKey,draftOwner;
    private StudentProfile submittedProfile;
    private long submittedVersion;
    private String submittedTerms;
    private boolean initialized,registration;
    private AuthRepository.PasswordChangeNotice passwordNotice;
    private int generation;
    public AuthViewModel(Application app) { this(app,new SavedStateHandle()); }
    public AuthViewModel(Application application,SavedStateHandle saved) {
        super(application); this.app=(GoServeApplication)application; this.auth=app.auth(); this.saved=saved;
        draft.email=text("email"); draft.recoveryEmail=text("recovery_email"); acceptedTerms=saved.get("terms_version");
        draft.profile=new StudentProfile(text("first_name"),text("middle_name"),Boolean.TRUE.equals(saved.get("no_middle_name")),text("last_name"),text("student_id"),text("course_program"),text("year_level"),text("mobile_number"),text("gender"));
        emit(Route.RESTORING,true,null);
    }
    private String text(String key) { String value=saved.get(key); return value==null?"":value; }
    public LiveData<State> state() { return state; }
    public LiveData<OneTime<Uri>> googleUrl() { return googleUrl; }
    public Draft draft() { return draft; }
    public String registrationPassword() { return registrationPassword; }
    public String registrationConfirmation() { return registrationConfirmation; }
    public String signinPassword() { return signinPassword; }
    public String resetPasswordDraft() { return resetPassword; }
    public String resetConfirmationDraft() { return resetConfirmation; }
    public boolean readOnlyTerms() { return termsMode==TermsMode.READ_ONLY; }
    public boolean registering() { return registration; }
    public boolean hasAuthenticatedProfile() { return auth.profile()!=null; }
    public void initialize(Uri callback) {
        if (initialized) return; initialized=true;
        if (callback!=null) { finishGoogle(callback); return; }
        passwordNotice=auth.consumePasswordChangeNotice();
        if(passwordNotice!=null) {
            clearSecrets(); saved.set("step",null); emit(Route.SIGN_IN,false,null); return;
        }
        work(() -> auth.resumeSession(null),result -> {
            if (result!=null) routeAccount((SessionProfile)result);
            else if ("CREDENTIALS".equals(saved.get("step")) || "PROFILE".equals(saved.get("step"))) {
                registration=true; emit(Route.CREDENTIALS,false,"Re-enter your password to continue your saved registration.");
            } else emit(Route.SIGN_IN,false,null);
        },Route.SESSION_RETRY);
    }
    public void retrySession() { if (!loading()) work(() -> auth.resumeSession(null),result -> { if (result==null) emit(Route.SIGN_IN,false,null); else routeAccount((SessionProfile)result); },Route.SESSION_RETRY); }
    public void password(String email,String password) {
        if (loading()) return; passwordNotice=null; draft.email=email; signinPassword=password; saveDraft();
        work(() -> auth.signInPassword(email,password),result -> { clearSecrets(); routeAccount((SessionProfile)result); },Route.SIGN_IN);
    }
    public void google() {
        if (loading()) return;
        passwordNotice=null;
        work(auth::googleAuthorizationUrl,result -> { emit(Route.SIGN_IN,false,null); googleUrl.setValue(new OneTime<>((Uri)result)); },Route.SIGN_IN);
    }
    public void finishGoogle(Uri callback) {
        if (callback==null || !callbacks.add(callback)) return;
        if (callbacks.size()>32) callbacks.remove(callbacks.iterator().next());
        boolean recovery="/recovery".equals(callback.getPath()) || "recovery".equals(callback.getQueryParameter("flow"));
        ++generation;
        try { if (recovery) auth.supersedeSession(); else auth.prepareGoogleCallback(); }
        catch (Exception error) { emit(recovery?Route.RESET_UNAVAILABLE:Route.SIGN_IN,false,message(error)); return; }
        if (recovery) {
            clearSecrets(); emit(Route.RESET_PASSWORD,true,null);
            work(() -> { auth.finishRecovery(callback); return null; },result -> emit(Route.RESET_PASSWORD,false,null),Route.RESET_UNAVAILABLE);
        } else work(() -> auth.resumeSession(callback),result -> { if (result==null) emit(Route.SIGN_IN,false,null); else routeAccount((SessionProfile)result); },Route.SIGN_IN);
    }
    private void routeAccount(SessionProfile profile) {
        if (profile!=auth.profile()) return;
        registration=false;
        if (profile.ready()) { clearSecrets(); app.bindCurrentAccount(); emit(Route.HOME,false,null); return; }
        if (!profile.id.equals(draftOwner)) {
            draftOwner=profile.id; draft.email=profile.email;
            if (profile.student!=null) draft.profile=profile.student;
            saveDraft();
        }
        if ("accept_terms".equals(profile.nextAction)) { termsMode=TermsMode.ACCOUNT; terms=null; emit(Route.TERMS,false,null); loadTerms(); }
        else emit(Route.PROFILE,false,null);
    }
    public void startRegistration() {
        if (loading()) return; clearSecrets(); registration=true; acceptedTerms=null; terms=null; termsMode=TermsMode.REGISTER; termsReturn=Route.SIGN_IN;
        emit(Route.TERMS,false,null); loadTerms();
    }
    public void showTerms(boolean readOnly) {
        if (loading() && state.getValue().route!=Route.RESTORING) return; termsReturn=state.getValue().route; termsMode=readOnly?TermsMode.READ_ONLY:TermsMode.REGISTER; terms=null;
        emit(Route.TERMS,false,null); loadTerms();
    }
    public void ensureTerms() { if (terms==null && !loading()) loadTerms(); }
    public void loadTerms() {
        if (loading()) return;
        work(auth::loadTerms,result -> { terms=(TermsDocument)result; emit(Route.TERMS,false,null); },Route.TERMS);
    }
    public void acceptTerms() {
        if (loading() || terms==null || readOnlyTerms()) return;
        if (termsMode==TermsMode.REGISTER) { acceptedTerms=terms.version; saved.set("terms_version",acceptedTerms); registration=true; emit(Route.CREDENTIALS,false,null); return; }
        SessionProfile current=auth.profile(); if (current==null) { signInScreen(); return; }
        String key=updateOperation(current.version,terms.version,null);
        work(() -> auth.updateAccount(current.version,terms.version,null,key),result -> routeAccount((SessionProfile)result),Route.TERMS);
    }
    public void acceptConsent() { acceptTerms(); }
    public void credentials(String email,String password,String confirmation) {
        setEmail(email); setRegistrationPasswords(password,confirmation);
        Map<String,String> errors=passwordErrors(password,confirmation);
        if (!AccountValidation.isValidEmail(email)) errors.put("email","Enter a valid email address.");
        if (!errors.isEmpty()) { fields(Route.CREDENTIALS,errors); return; }
        if (acceptedTerms==null) { startRegistration(); return; }
        registration=true; emit(Route.PROFILE,false,null);
    }
    public void setEmail(String email) { if (!Objects.equals(draft.email,email)) registrationKey=null; draft.email=email; saveDraft(); }
    public void setRegistrationPasswords(String password,String confirmation) {
        if (!Objects.equals(registrationPassword,password)) registrationKey=null;
        registrationPassword=password; registrationConfirmation=confirmation;
    }
    public void setSigninPassword(String value) { signinPassword=value; }
    public void setResetPasswords(String password,String confirmation) { resetPassword=password; resetConfirmation=confirmation; }
    public void saveProfileDraft(StudentProfile profile) {
        if (!sameProfile(draft.profile,profile)) registrationKey=null;
        draft.profile=profile; saveDraft();
    }
    public void submitProfile(StudentProfile profile) {
        if (loading()) return; saveProfileDraft(profile);
        Map<String,String> errors=new LinkedHashMap<>();
        for (Map.Entry<String,AccountValidation.ProfileError> error:AccountValidation.profileErrors(profile).entrySet())
            errors.put(error.getKey(),error.getValue()==AccountValidation.ProfileError.MIDDLE_NAME_CONFLICT?"Clear the middle name or turn off No middle name.":"Enter "+label(error.getKey())+".");
        if (!errors.isEmpty()) { fields(Route.PROFILE,errors); return; }
        if (registration) {
            if (registrationPassword.isEmpty()) { emit(Route.CREDENTIALS,false,"Re-enter your password to continue."); return; }
            if (registrationKey==null) registrationKey=UUID.randomUUID().toString();
            String email=draft.email,password=registrationPassword,key=registrationKey,version=acceptedTerms;
            work(() -> auth.registerStudent(email,password,profile,version,key),result -> { draft.email=(String)result; clearSecrets(); emit(Route.ACCOUNT_CREATED,false,null); },Route.PROFILE);
        } else {
            SessionProfile current=auth.profile(); if (current==null) { signInScreen(); return; }
            String key=updateOperation(current.version,null,profile);
            work(() -> auth.updateAccount(current.version,null,profile,key),result -> routeAccount((SessionProfile)result),Route.PROFILE);
        }
    }
    private String updateOperation(long version,String terms,StudentProfile profile) {
        if (updateKey==null || version!=submittedVersion || !Objects.equals(terms,submittedTerms) || !sameProfile(profile,submittedProfile)) updateKey=UUID.randomUUID().toString();
        submittedVersion=version; submittedTerms=terms; submittedProfile=profile; return updateKey;
    }
    private static boolean sameProfile(StudentProfile a,StudentProfile b) {
        if (a==b) return true; if (a==null || b==null) return false;
        return a.noMiddleName==b.noMiddleName && Objects.equals(a.firstName,b.firstName) && Objects.equals(a.middleName,b.middleName) && Objects.equals(a.lastName,b.lastName)
                && Objects.equals(a.studentId,b.studentId) && Objects.equals(a.courseProgram,b.courseProgram) && Objects.equals(a.yearLevel,b.yearLevel)
                && Objects.equals(a.mobileNumber,b.mobileNumber) && Objects.equals(a.gender,b.gender);
    }
    public void recoveryEmail() { ++generation; clearSecrets(); emit(Route.RESET_EMAIL,false,null); }
    public void setRecoveryEmail(String email) { draft.recoveryEmail=email; saveDraft(); }
    public void requestRecovery() {
        if (loading()) return;
        if (!AccountValidation.isValidEmail(draft.recoveryEmail)) { fields(Route.RESET_EMAIL,Collections.singletonMap("email","Enter a valid email address.")); return; }
        String email=draft.recoveryEmail;
        work(() -> { auth.requestRecovery(email); return null; },result -> emit(Route.RESET_SENT,false,null),Route.RESET_EMAIL);
    }
    public void resetPassword(String password,String confirmation) {
        if (loading()) return; setResetPasswords(password,confirmation);
        Map<String,String> errors=passwordErrors(password,confirmation); if (!errors.isEmpty()) { fields(Route.RESET_PASSWORD,errors); return; }
        work(() -> { auth.resetPassword(password,confirmation); return null; },result -> { clearSecrets(); emit(Route.RESET_COMPLETE,false,null); },Route.RESET_PASSWORD);
    }
    public void signInScreen() {
        ++generation; clearSecrets(); registration=false; saved.set("step",null);
        if (auth.profile()==null) auth.invalidateSession();
        emit(Route.SIGN_IN,false,null);
    }
    public boolean back() {
        Route route=state.getValue().route;
        if (route==Route.SIGN_IN) return false;
        if (loading()) { ++generation; auth.invalidateSession(); signInScreen(); return true; }
        if (route==Route.TERMS && readOnlyTerms()) emit(termsReturn==Route.RESTORING?Route.SIGN_IN:termsReturn,false,null);
        else if (route==Route.PROFILE && registration) emit(Route.CREDENTIALS,false,null);
        else if (route==Route.CREDENTIALS) { termsMode=TermsMode.REGISTER; emit(Route.TERMS,false,null); if (terms==null) loadTerms(); }
        else { auth.invalidateSession(); signInScreen(); }
        return true;
    }
    private void clearSecrets() { registrationPassword=""; registrationConfirmation=""; signinPassword=""; resetPassword=""; resetConfirmation=""; registrationKey=null; }
    private Map<String,String> passwordErrors(String password,String confirmation) {
        Map<String,String> errors=new LinkedHashMap<>(); AccountValidation.PasswordError error=AccountValidation.passwordError(password,confirmation);
        if (error!=null) errors.put(error==AccountValidation.PasswordError.CONFIRMATION_MISMATCH?"confirmation":"password",
                error==AccountValidation.PasswordError.CONFIRMATION_MISMATCH?"Passwords do not match.":"Use at least 6 characters with a letter, a decimal digit and a punctuation or symbol.");
        return errors;
    }
    private String label(String field) { return field.replace('_',' '); }
    private boolean loading() { return state.getValue()!=null && state.getValue().loading; }
    private void emit(Route route,boolean loading,String error) {
        state.setValue(new State(route,loading,error,auth.profile(),terms,Collections.emptyMap(),route==Route.SIGN_IN?passwordNotice:null));
        if (registration && (route==Route.CREDENTIALS || route==Route.PROFILE)) saved.set("step",route.name());
        else if (route==Route.ACCOUNT_CREATED || route==Route.HOME || route==Route.SIGN_IN) saved.set("step",null);
    }
    private void fields(Route route,Map<String,String> errors) { state.setValue(new State(route,false,null,auth.profile(),terms,errors,null)); }
    private void saveDraft() {
        saved.set("email",draft.email); saved.set("recovery_email",draft.recoveryEmail); StudentProfile p=draft.profile;
        saved.set("first_name",p.firstName); saved.set("middle_name",p.middleName); saved.set("no_middle_name",p.noMiddleName); saved.set("last_name",p.lastName);
        saved.set("student_id",p.studentId); saved.set("course_program",p.courseProgram); saved.set("year_level",p.yearLevel); saved.set("mobile_number",p.mobileNumber); saved.set("gender",p.gender);
    }
    private interface Work { Object run() throws Exception; }
    private interface Success { void accept(Object result); }
    private void work(Work operation,Success success,Route failureRoute) {
        int request=++generation; Route route=state.getValue().route; emit(route,true,null);
        executor.execute(() -> {
            try {
                Object result=operation.run(); long accountGeneration=auth.generation();
                main.post(() -> {
                    if (request!=generation || accountGeneration!=auth.generation()) return;
                    if (result instanceof SessionProfile && result!=auth.profile()) return;
                    try { success.accept(result); } catch (Exception error) { emit(failureRoute,false,message(error)); }
                });
            } catch (Exception error) { main.post(() -> {
                if (request!=generation) return;
                if (failureRoute==Route.RESET_PASSWORD && (error instanceof AuthRepository.RecoveryUnavailableException
                        || error instanceof SupabaseClient.ApiException && (((SupabaseClient.ApiException)error).status==401 || ((SupabaseClient.ApiException)error).status==403))) {
                    clearSecrets(); emit(Route.RESET_UNAVAILABLE,false,message(error));
                } else emit(failureRoute,false,message(error));
            }); }
        });
    }
    private String message(Exception error) {
        return error instanceof IllegalArgumentException || error instanceof IllegalStateException || error instanceof SupabaseClient.ApiException
                ? error.getMessage():"Could not connect to GoServe. Check your connection and retry.";
    }
    @Override protected void onCleared() { ++generation; clearSecrets(); callbacks.clear(); executor.shutdownNow(); }
}
