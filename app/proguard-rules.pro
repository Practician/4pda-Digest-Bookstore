-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.bookparser.digest.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
