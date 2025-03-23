package com.nklcbdty.api.crawler.vo;

import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;

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
