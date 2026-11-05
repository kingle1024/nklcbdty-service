package com.nklcbdty.api.resumereview.service;

/**
 * 첨삭을 못 하는 이유. 메시지는 그대로 사용자에게 보여 주므로 한국어 문장으로 쓴다.
 */
public class ResumeReviewException extends RuntimeException {

    public enum Kind {
        /** 입력이 잘못됐다(파일 형식·크기, 공고 주소 등). 사용자가 고칠 수 있다 → 400 */
        BAD_INPUT,
        /** AI 가 답을 못 줬다. 잠시 뒤 다시 하면 된다 → 409 */
        UNAVAILABLE
    }

    private final Kind kind;

    public ResumeReviewException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public static ResumeReviewException badInput(String message) {
        return new ResumeReviewException(Kind.BAD_INPUT, message);
    }

    public static ResumeReviewException unavailable(String message) {
        return new ResumeReviewException(Kind.UNAVAILABLE, message);
    }

    public Kind getKind() {
        return kind;
    }
}
