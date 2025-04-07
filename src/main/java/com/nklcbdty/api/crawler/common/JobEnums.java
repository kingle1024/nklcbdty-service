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
    AI("AI"),
    Infra("Infra"),
    DataAnalyst("DataAnalyst"),
    DataEngineering("DataEngineering"),
    FullStack("FullStack"),
    DBA("DBA"),
    SecurityEngineering("SecurityEngineering"),
    Security("Security"),
    SAP("SAP"),
    ML("ML"),
    QA("QA"),
    TechnicalSupport("TechnicalSupport"),
    PM("PM"),
    Flutter("Flutter"),
    ProductDesigner("ProductDesigner"),
    PO("PO")
    ;

    private final String title;

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
