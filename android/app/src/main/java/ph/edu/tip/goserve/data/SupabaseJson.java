package ph.edu.tip.goserve.data;

import org.json.JSONTokener;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared response parser; decimal tokens must remain lossless for money consumers. */
public final class SupabaseJson {
    private SupabaseJson() {}
    private static final Pattern NUMBER=Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");

    public static Object parse(String response) throws Exception {
        StringBuilder exact=new StringBuilder(response.length());
        boolean quoted=false, escaped=false;
        for (int index=0;index<response.length();) {
            char character=response.charAt(index);
            if (quoted) {
                exact.append(character); ++index;
                if (escaped) escaped=false;
                else if (character=='\\') escaped=true;
                else if (character=='"') quoted=false;
                continue;
            }
            if (character=='"') { quoted=true; exact.append(character); ++index; continue; }
            if (character=='-' || character>='0' && character<='9') {
                Matcher match=NUMBER.matcher(response).region(index,response.length());
                if (!match.lookingAt()) throw new JSONException("Invalid response number.");
                String token=match.group(); int end=match.end();
                if (end<response.length() && ",]} \t\r\n".indexOf(response.charAt(end))<0) throw new JSONException("Invalid response number.");
                boolean preserve=token.indexOf('.')>=0 || token.indexOf('e')>=0 || token.indexOf('E')>=0;
                if (!preserve) try { Long.parseLong(token); } catch (NumberFormatException overflow) { preserve=true; }
                exact.append(preserve?JSONObject.quote(token):token); index=end; continue;
            }
            exact.append(character); ++index;
        }
        JSONTokener parser=new JSONTokener(exact.toString()); Object result=parser.nextValue();
        if (parser.nextClean()!=0) throw new JSONException("Unexpected response content.");
        return result;
    }
}
