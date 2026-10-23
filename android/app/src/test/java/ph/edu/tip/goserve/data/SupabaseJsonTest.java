package ph.edu.tip.goserve.data;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public final class SupabaseJsonTest {
    @Test public void preservesMoneyExponentAndOverflowTokensBeforeFloatingPointConversion() throws Exception {
        JSONObject result=(JSONObject)SupabaseJson.parse("{\"amount\":12345678901234567890.123456789,\"tiny\":1.234567890123456789e-12,\"overflow\":9223372036854775808,\"version\":3,\"count\":7}");
        assertTrue("Amount must be exact text, never Double.",result.get("amount") instanceof String);
        assertEquals("12345678901234567890.123456789",result.getString("amount"));
        assertEquals("1.234567890123456789e-12",result.getString("tiny"));
        assertEquals("9223372036854775808",result.getString("overflow"));
        assertTrue(result.get("version") instanceof Number); assertEquals(3,result.getLong("version"));
        assertTrue(result.get("count") instanceof Number); assertEquals(7,result.getLong("count"));
    }
    @Test public void keepsQuotedEscapedTextAndIntegerScalarsWhilePreservingDecimalArrayValues() throws Exception {
        String label="Cost 12.30, quote \"1e2\", path C:\\12.3 and newline\nthen -3.40";
        JSONObject source=new JSONObject().put("label",label).put("escaped","\\\"42.01\\\"");
        String response="["+source+",0.100000000000000005,-2.50E+3,17]";
        JSONArray result=(JSONArray)SupabaseJson.parse(response);
        assertEquals(label,result.getJSONObject(0).getString("label"));
        assertEquals(source.getString("escaped"),result.getJSONObject(0).getString("escaped"));
        assertEquals("0.100000000000000005",result.getString(1)); assertEquals("-2.50E+3",result.getString(2));
        assertTrue(result.get(3) instanceof Number);
        assertEquals(23,((Number)SupabaseJson.parse("23")).intValue());
        assertEquals(9223372036854775807L,((Number)SupabaseJson.parse("9223372036854775807")).longValue());
    }
}
