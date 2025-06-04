package com.nklcbdty.api.crawler.vo;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

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
