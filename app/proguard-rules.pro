# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# vivo系统兼容性配置
-dontwarn com.vivo.**
-keep class com.vivo.** { *; }
-dontwarn com.bbk.**
-keep class com.bbk.** { *; }

# 邮件相关keep规则
-keep class javax.mail.** { *; }
-keep class com.sun.mail.** { *; }
-keep class javax.activation.** { *; }
-dontwarn javax.mail.**
-dontwarn com.sun.mail.**
-dontwarn javax.activation.**

# 短信接收器
-keep class com.example.v11.SmsReceiver { *; }
-keep class com.example.v11.EmailService { *; }
-keep class com.example.v11.MyApplication { *; }

# 通知相关
-keep class androidx.core.app.NotificationCompat** { *; }

# MultiDex支持
-keep class androidx.multidex.** { *; }
-dontwarn androidx.multidex.**

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile