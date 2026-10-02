package org.example.ai.guest;

public class GuestTrialException extends RuntimeException {
    private final String code;
    private final int status;
    public GuestTrialException(String code, int status) {
        super(code);
        this.code = code;
        this.status = status;
    }
    public String code() { return code; }
    public int status() { return status; }
}
