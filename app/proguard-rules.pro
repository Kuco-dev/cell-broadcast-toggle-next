# Conscrypt references platform-internal SSL classes that only exist on old Android versions.
-dontwarn com.android.org.conscrypt.SSLParametersImpl
-dontwarn org.apache.harmony.xnet.provider.jsse.SSLParametersImpl

# libadb looks these up by name: the TLS provider and Conscrypt.exportKeyingMaterial for pairing.
-keep class org.conscrypt.OpenSSLProvider { <init>(); }
-keep class org.conscrypt.Conscrypt { public static *; }
