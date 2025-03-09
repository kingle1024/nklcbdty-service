package com.nklcbdty.api.crawler.common;

public enum JobEnums implements EnumMapperType {

    REGULAR("정규"),
    CONTRACT("비정규"),
    SEOUL("서울"),
    Backend("Backend"),
    iOS("iOS"),
    Android("Android"),
    DevOps("DevOps"),
    DataAnalyst("DataAnalyst"),
    DataScientist("DataScientist"),
    FullStack("FullStack"),
    DBA("DBA"),
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
