
-keep public class com.forjrking.lubankt.Checker { *; }
-keep public class com.forjrking.lubankt.io.ArrayProvide { *; }
-keepclasseswithmembernames class * { ####
    native <methods>;
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}