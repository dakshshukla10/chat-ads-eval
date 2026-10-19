package dev.chatads.eval;

public record CheckResult(String name, boolean passed, String detail) {

    public static CheckResult pass(String name) {
        return new CheckResult(name, true, "ok");
    }

    public static CheckResult fail(String name, String detail) {
        return new CheckResult(name, false, detail);
    }
}
