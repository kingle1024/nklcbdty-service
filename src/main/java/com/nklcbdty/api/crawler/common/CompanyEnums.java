package com.nklcbdty.api.crawler.common;

import lombok.Getter;

/**
 * 크롤링 대상 회사와 각 회사의 채용 페이지 주소.
 *
 * <p>코드(companyCd)는 {@code job_mst.company_cd} 및 {@code /api/crawler?company=} 파라미터와 같은 값이다.
 * 채용 페이지 주소는 "채용 페이지로 가기" 버튼에서 쓰이며, 크롤러가 보는 API 가 아니라
 * 사람이 보는 목록 페이지를 가리킨다.</p>
 */
@Getter
public enum CompanyEnums {

    NAVER("네이버", "https://recruit.navercorp.com/rcrt/list.do"),
    KAKAO("카카오", "https://careers.kakao.com/jobs"),
    LINE("라인", "https://careers.linecorp.com/ko/jobs"),
    COUPANG("쿠팡", "https://www.coupang.jobs/kr/jobs/"),
    BAEMIN("배달의민족", "https://career.woowahan.com/"),
    // 채용 사이트가 about.daangn.com 에서 careers.daangn.com 으로 이전됨
    DAANGN("당근마켓", "https://careers.daangn.com/jobs/"),
    TOSS("토스", "https://toss.im/career/jobs"),
    // careers.yanolja.co 에는 공고 목록이 없다. 지원은 Workday 채용 사이트에서 받는다.
    YANOLJA("야놀자", "https://yanolja.wd102.myworkdayjobs.com/ko-KR/External_Yanolja"),
    ;

    private final String companyNm;
    private final String careerPageUrl;

    CompanyEnums(String companyNm, String careerPageUrl) {
        this.companyNm = companyNm;
        this.careerPageUrl = careerPageUrl;
    }

    public String getCompanyCd() {
        return name();
    }
}
