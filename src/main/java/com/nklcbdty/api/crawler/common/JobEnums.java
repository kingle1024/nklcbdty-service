package com.nklcbdty.api.crawler.common;

public enum JobEnums implements EnumMapperType {

    REGULAR("정규"),
    CONTRACT("비정규"),
    SEOUL("서울"),
    BackEnd("Backend"),
    FrontEnd("FrontEnd"),
    iOS("iOS"),
    Android("Android"),
    DevOps("DevOps"),
    Infra("Infra"),
    DataAnalyst("DataAnalyst"),
    DataScientist("DataScientist"),
    FullStack("FullStack"),
    DBA("DBA"),
    SecurityEngineering("SecurityEngineering"),
    Security("Security"),
    ML("ML"),
    TechnicalSupport("TechnicalSupport"),
    PM("PM"),
    ;

    private String title;

    JobEnums(String title) {
        this.title = title;
    }

    @Override
    public String getCode() {
        return name();
    }

    @Override
    public String getTitle() {
        return title;
    }
}
