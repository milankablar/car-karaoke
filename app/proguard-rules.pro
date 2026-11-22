# Preserve app models and provider reflection while shrinking unused dependency code.
-keep class io.github.milankablar.carkaraoke.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,AnnotationDefault
