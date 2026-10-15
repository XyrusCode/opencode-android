# OkHttp ships optional references to Conscrypt/BouncyCastle providers that are
# absent at runtime; these rules keep R8 from warning about them.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
