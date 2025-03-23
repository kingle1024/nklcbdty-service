package com.nklcbdty.api.crawler.vo;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonBackReference;

import lombok.Getter;

@Entity
@Table(name = "category_dtl")
@Getter
public class CategoryDtl {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String rank;

    @ManyToOne
    @JoinColumn(name = "category_id")
    @JsonBackReference
    private CategoryMst categoryMst;
}
