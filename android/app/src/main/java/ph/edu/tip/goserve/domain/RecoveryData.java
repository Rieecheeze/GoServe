package ph.edu.tip.goserve.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class RecoveryData {
    private RecoveryData(){}
    public static final class Status {
        public final boolean codesConfigured,codesAcknowledged,requiresSetup;
        public final int remainingCodes;
        public final String generation;
        public Status(boolean configured,boolean acknowledged,int remaining,String generation,boolean required){codesConfigured=configured;codesAcknowledged=acknowledged;remainingCodes=remaining;this.generation=generation;requiresSetup=required;}
    }
    public static final class Codes {
        public final String generation;
        public final List<String> codes;
        public final int remainingCodes;
        public Codes(String generation,List<String> codes,int remaining){this.generation=generation;this.codes=java.util.Collections.unmodifiableList(new ArrayList<>(codes));remainingCodes=remaining;}
    }
    public static final class ResetResult {
        public final String state,operationId;
        public ResetResult(String state,String id){this.state=state;operationId=id;}
    }
    public static Status status(JSONObject value){
        try{
            exact(value,"codes_configured","codes_acknowledged","remaining_codes","generation","requires_setup");
            boolean configured=bool(value,"codes_configured"),acknowledged=bool(value,"codes_acknowledged"),required=bool(value,"requires_setup");
            int remaining=count(value);String generation=value.isNull("generation")?null:uuid(text(value,"generation"));
            if(!configured && (acknowledged || remaining!=0 || generation!=null) || configured && generation==null || required!=(!configured || !acknowledged || remaining==0))throw invalid();
            return new Status(configured,acknowledged,remaining,generation,required);
        }catch(Exception malformed){throw invalid();}
    }
    public static Codes codes(JSONObject value){
        try{
            exact(value,"generation","codes","remaining_codes","codes_configured","codes_acknowledged");
            if(!bool(value,"codes_configured") || bool(value,"codes_acknowledged") || count(value)!=10)throw invalid();
            String generation=uuid(text(value,"generation"));JSONArray values=value.getJSONArray("codes");if(values.length()!=10)throw invalid();
            List<String> codes=new ArrayList<>();HashSet<String> unique=new HashSet<>();
            for(int i=0;i<10;i++){if(!(values.get(i) instanceof String))throw invalid();String code=values.getString(i);if(!code.matches("[A-Z2-7]{4}(?:-[A-Z2-7]{4}){7}") || !unique.add(code))throw invalid();codes.add(code);}
            return new Codes(generation,codes,10);
        }catch(Exception malformed){throw invalid();}
    }
    public static ResetResult resetResult(JSONObject value,String expected){
        try{exact(value,"state","operation_id");String state=text(value,"state"),id=uuid(text(value,"operation_id"));if(!Arrays.asList("completed","pending","failed").contains(state) || !id.equals(expected))throw invalid();return new ResetResult(state,id);}
        catch(Exception malformed){throw invalid();}
    }
    public static void exact(JSONObject value,String...keys){if(value==null || value.length()!=keys.length)throw invalid();for(String key:keys)if(!value.has(key))throw invalid();}
    public static String text(JSONObject value,String key){Object result=value.opt(key);if(!(result instanceof String) || ((String)result).isEmpty())throw invalid();return (String)result;}
    public static String uuid(String value){if(value==null || !value.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[1-5][a-fA-F0-9]{3}-[89aAbB][a-fA-F0-9]{3}-[a-fA-F0-9]{12}"))throw invalid();return value;}
    private static boolean bool(JSONObject value,String key){Object result=value.opt(key);if(!(result instanceof Boolean))throw invalid();return (Boolean)result;}
    private static int count(JSONObject value){Object result=value.opt("remaining_codes");if(!(result instanceof Integer || result instanceof Long) || ((Number)result).longValue()<0 || ((Number)result).longValue()>10)throw invalid();return ((Number)result).intValue();}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("The recovery response could not be verified. Refresh recovery status before continuing.");}
}
