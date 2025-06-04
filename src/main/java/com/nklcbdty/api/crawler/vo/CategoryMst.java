package com.nklcbdty.api.crawler.vo;

import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonManagedReference;

import lombok.Getter;

@Entity
@Table(name = "category_mst")
@Getter
public class CategoryMst {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private boolean isVisible;

    private String rank;

    @OneToMany(mappedBy = "categoryMst", fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<CategoryDtl> categoryDtls;
}
