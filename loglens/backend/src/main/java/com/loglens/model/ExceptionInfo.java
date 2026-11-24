package com.loglens.model;

public record ExceptionInfo(String type, String message) {

    public static final String UNKNOWN_TYPE = "(unknown exception type)";

    public ExceptionInfo {
        type = (type == null || type.isBlank()) ? UNKNOWN_TYPE : type;
        message = message == null ? "" : message;
    }

    /** Class name without package, e.g. "NullPointerException". */
    public String simpleName() {
        int i = Math.max(type.lastIndexOf('.'), type.lastIndexOf('$'));
        return type.substring(i + 1);
    }
}
