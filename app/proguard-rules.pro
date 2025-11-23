# proguard-rules.pro
# Add project specific ProGuard rules here.

# Keep all classes in our package
-keep class com.superposition.ai.** { *; }

# Keep Material Components
-keep class com.google.android.material.** { *; }

# Keep Billing
-keep class com.android.billingclient.** { *; }
