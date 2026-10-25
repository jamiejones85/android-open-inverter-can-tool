# kotlinx.serialization
-keepattributes *Annotation*,Signature,InnerClasses
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembernames class * {
    @kotlinx.serialization.SerialName <fields>;
}

# usb-serial-for-android
-keep class com.hoho.android.usbserial.** { *; }

# openinverter data models and upgrader
-keep class app.oic.oi.** { *; }
-keep class app.oic.can.** { *; }
