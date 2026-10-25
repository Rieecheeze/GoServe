package ph.edu.tip.goserve.ui;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LegalTextRenderer {
    private static final Pattern HEADING=Pattern.compile("^ {0,3}(#{1,6})[ \\t]+([^\\r\\n]+)(\\r?)$");
    private LegalTextRenderer() {}

    static CharSequence render(String source) {
        SpannableStringBuilder display=new SpannableStringBuilder();
        String[] lines=source.split("\\n",-1);
        boolean hasLine=false,previousTableRow=false;
        for(int index=0;index<lines.length;index++) {
            String[] cells=tableCells(lines[index]);
            if(cells!=null&&(separator(cells)||index+1<lines.length&&separator(tableCells(lines[index+1]))))continue;
            if(hasLine)display.append('\n');
            if(previousTableRow&&cells!=null)display.append('\n');
            hasLine=true;previousTableRow=cells!=null;
            if(cells!=null) {
                int start=display.length();display.append(cells[0]);
                display.setSpan(new StyleSpan(Typeface.BOLD),start,display.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                display.append('\n');
                for(int column=1;column<cells.length;column++) {
                    if(column>1)display.append(" · ");
                    display.append(cells[column]);
                }
                continue;
            }
            Matcher heading=HEADING.matcher(lines[index]);
            if(!heading.matches()){display.append(lines[index]);continue;}
            int start=display.length();display.append(heading.group(2));
            display.setSpan(new StyleSpan(Typeface.BOLD),start,display.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            display.setSpan(new RelativeSizeSpan(heading.group(1).length()==1?1.15f:1.08f),start,display.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            display.append(heading.group(3));
        }
        return display;
    }

    private static String[] tableCells(String line) {
        String value=line.trim();
        if(!value.startsWith("|")||!value.endsWith("|")||value.length()<3)return null;
        String[] cells=value.substring(1,value.length()-1).split("\\|",-1);
        if(cells.length<2)return null;
        for(int index=0;index<cells.length;index++)cells[index]=cells[index].trim();
        return cells;
    }

    private static boolean separator(String[] cells) {
        if(cells==null)return false;
        for(String cell:cells)if(!cell.matches(":?-{3,}:?"))return false;
        return true;
    }
}
