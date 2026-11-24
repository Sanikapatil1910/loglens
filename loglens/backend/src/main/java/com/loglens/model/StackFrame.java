package com.loglens.model;

public record StackFrame(
        String className,
        String methodName,
        String fileName,
        Integer lineNumber,
        boolean applicationFrame) {

    /** Human-readable form, e.g. com.example.UserService.getUser(UserService.java:42). */
    public String displayName() {
        String location = fileName == null ? "Unknown Source"
                : lineNumber == null ? fileName : fileName + ":" + lineNumber;
        return className + "." + methodName + "(" + location + ")";
    }
}
