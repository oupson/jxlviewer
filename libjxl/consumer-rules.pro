-keep class fr.oupson.libjxl.exceptions.DecodeError { <init>(int); }
-keep class fr.oupson.libjxl.exceptions.DecodeError { <init>(int,java.lang.String); }

-keep class fr.oupson.libjxl.** {
    static native <methods>;
}