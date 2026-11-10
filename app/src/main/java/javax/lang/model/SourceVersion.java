package javax.lang.model;

public enum SourceVersion {
    RELEASE_0,
    RELEASE_1,
    RELEASE_2,
    RELEASE_3,
    RELEASE_4,
    RELEASE_5,
    RELEASE_6,
    RELEASE_7,
    RELEASE_8,
    RELEASE_9,
    RELEASE_10,
    RELEASE_11,
    RELEASE_12,
    RELEASE_13,
    RELEASE_14,
    RELEASE_15,
    RELEASE_16,
    RELEASE_17,
    RELEASE_18,
    RELEASE_19,
    RELEASE_20,
    RELEASE_21;

    public static SourceVersion latest() {
        return RELEASE_21;
    }

    public static SourceVersion latestSupported() {
        return RELEASE_21;
    }

    public static boolean isIdentifier(CharSequence name) {
        return name != null && name.length() > 0;
    }

    public static boolean isName(CharSequence name) {
        return name != null && name.length() > 0;
    }

    public static boolean isKeyword(CharSequence s) {
        return false;
    }
}
