package ph.edu.tip.goserve.data;

import org.json.JSONObject;

public final class SeryoRepository {
    public interface Transport {
        JSONObject control(JSONObject input) throws Exception;
        default JSONObject send(JSONObject input) throws Exception { throw new IllegalStateException("Seryo send is unavailable."); }
        void requireCurrent();
    }
    public static final class Readiness {
        public final String consent, cleanup;
        public final long version;
        public final boolean providerReady, canSend, canAttach;
        Readiness(String consent,long version,boolean providerReady,boolean canSend,boolean canAttach,String cleanup) {
            this.consent=consent; this.version=version; this.providerReady=providerReady; this.canSend=canSend; this.canAttach=canAttach; this.cleanup=cleanup;
        }
        public boolean allowsAttachment() { return "allow".equals(consent) && providerReady && canAttach && "clear".equals(cleanup); }
    }
    private final Transport transport;
    public SeryoRepository(AccountGateway gateway) { this(new Transport() {
        public JSONObject control(JSONObject input)throws Exception { return gateway.edge("seryo-control",input); }
        public JSONObject send(JSONObject input)throws Exception { return gateway.edge("seryo-send",input); }
        public void requireCurrent() { gateway.requireCurrent(); }
    }); }
    public SeryoRepository(Transport transport) { this.transport=transport; }
    public void requireCurrent() { transport.requireCurrent(); }
    public String send(String question,String key)throws Exception {
        if(question==null || question.trim().isEmpty() || question.length()>2000 || key==null
                || !key.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))
            throw new IllegalArgumentException("Enter a service question.");
        requireCurrent();JSONObject value=transport.send(new JSONObject().put("question",question).put("idempotency_key",key));requireCurrent();
        if(value==null || value.length()!=2 || !(value.opt("reply") instanceof String) || !(value.opt("refused") instanceof Boolean))
            throw new IllegalArgumentException("Seryo reply could not be verified.");
        String reply=value.getString("reply");
        if(reply.trim().isEmpty() || reply.length()>8000 || reply.matches("(?s).*[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f\\x7f].*"))
            throw new IllegalArgumentException("Seryo reply could not be verified.");
        return reply;
    }
    public Readiness readiness() throws Exception { return control(new JSONObject().put("kind","readiness")); }
    public Readiness consent(String decision,long version,String key)throws Exception {
        if (!java.util.Arrays.asList("allow","decline","revoke").contains(decision) || version<0 || key==null
                || !key.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))
            throw new IllegalArgumentException("Invalid consent decision.");
        return control(new JSONObject().put("kind","consent").put("decision",decision).put("expected_version",version).put("idempotency_key",key));
    }
    private Readiness control(JSONObject input)throws Exception {
        requireCurrent();JSONObject value=transport.control(input);requireCurrent();return parse(value);
    }
    public static Readiness parse(JSONObject value) {
        try {
            if(value==null || value.length()!=7) throw new IllegalArgumentException();
            Object version=value.get("consent_version");
            if(!(version instanceof Integer || version instanceof Long) || ((Number)version).longValue()<0) throw new IllegalArgumentException();
            String consent=value.getString("consent"),cleanup=value.getString("cleanup");
            if(!java.util.Arrays.asList("unset","allow","decline","revoke").contains(consent) || !java.util.Arrays.asList("clear","unresolved").contains(cleanup)) throw new IllegalArgumentException();
            for(String key:new String[]{"provider_ready","can_send","can_attach"}) if(!(value.get(key) instanceof Boolean)) throw new IllegalArgumentException();
            org.json.JSONArray blockers=value.getJSONArray("blockers");
            for(int i=0;i<blockers.length();i++) if(!(blockers.get(i) instanceof String)
                    || !java.util.Arrays.asList("PROVIDER_UNAVAILABLE","CLEANUP_REQUIRED","CONSENT_REQUIRED").contains(blockers.getString(i))) throw new IllegalArgumentException();
            return new Readiness(consent,((Number)version).longValue(),value.getBoolean("provider_ready"),value.getBoolean("can_send"),value.getBoolean("can_attach"),cleanup);
        } catch(Exception invalid) { throw new IllegalArgumentException("Seryo readiness could not be verified."); }
    }
}
