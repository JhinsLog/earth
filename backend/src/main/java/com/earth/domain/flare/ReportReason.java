package com.earth.domain.flare;

/**
 * 신고 사유(V16, D-24). 거짓 정보는 사실 확인의 "사실과 달라요"(D-20)가 맡으므로 여기 두지 않는다 — 신고는 "보이면 안 되는 것"이다.
 */
public enum ReportReason {
    /** 범죄 등 사건의 정확한 위치로 피해자·주민·집이 특정될 수 있다. */
    LOCATION_EXPOSURE,
    /** 이름·연락처 등 개인정보가 담겼다. */
    PERSONAL_INFO,
    /** 욕설·비방·혐오. */
    ABUSE
}
