# The app has no reflection, no serialisation library and no JNI, so nothing here has to
# survive by name for the app itself. Compose, AndroidX and kotlinx-coroutines each ship
# their own consumer rules inside their artifacts; R8 applies those automatically.
#
# The one thing worth keeping is the WebView side. Google's sign-in page is loaded into a
# WebView, and while this app installs no @JavascriptInterface bridge today, adding one
# later and having R8 quietly strip the annotated methods is a silent failure that looks
# like "Google changed something". The rule costs nothing now and forecloses that.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep line numbers so a crash report from a phone we cannot attach a debugger to still
# names a line. The source file name itself is stripped.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
