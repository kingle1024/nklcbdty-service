package com.nklcbdty.api.log.entity;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "visitor")
@Builder
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
    private String timestamp; // 방문 시간
}
