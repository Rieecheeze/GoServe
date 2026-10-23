package ph.edu.tip.goserve.data;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@Implements(className="androidx.core.content.FileProvider$SimplePathStrategy",isInAndroidSdk=false)
public final class AndroidFileProviderPaths {
    public static void reset() {
        ((java.util.Map<?,?>)org.robolectric.util.ReflectionHelpers.getStaticField(androidx.core.content.FileProvider.class,"sCache")).clear();
    }
    // AndroidX compares '/' boundaries; Robolectric's Windows filesystem returns '\\'.
    @Implementation protected boolean belongsToRoot(String filePath,String rootPath) {
        String file=filePath.replace('\\','/'),root=rootPath.replace('\\','/');
        while(file.endsWith("/"))file=file.substring(0,file.length()-1);
        while(root.endsWith("/"))root=root.substring(0,root.length()-1);
        return file.equals(root) || file.startsWith(root+'/');
    }
}
