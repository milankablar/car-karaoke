# Preserve app models and provider reflection while shrinking unused dependency code.
-keep class io.github.milankablar.carkaraoke.** { *; }
# OpenCC discovers conversion implementations reflectively.
-keep class com.github.houbb.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,AnnotationDefault
# Optional desktop-only helpers in OpenCC's transitive utility jar are not used on Android.
-dontwarn com.huaban.analysis.jieba.JiebaSegmenter
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.PropertyDescriptor
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
-dontwarn sun.misc.BASE64Decoder
-dontwarn sun.misc.BASE64Encoder
-dontwarn sun.reflect.generics.reflectiveObjects.WildcardTypeImpl
