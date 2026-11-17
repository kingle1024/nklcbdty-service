package com.nklcbdty.api.log.entity;

import java.util.Date;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "visitor")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VisitorEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String path;
    private String accept_language;
    private String referer;
    private String country_name;
    private String region_name;
    private String city_name;
    private String insert_ip;
    private Date insert_dts; // 방문 시간
    /** 접속 출처(IpOrigin 이름). 컬럼은 VisitorSchemaInitializer 가 만든다. */
    private String ip_origin;
}
