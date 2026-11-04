package com.nklcbdty.api.companyfeature.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nklcbdty.api.companyfeature.vo.CompanyFeature;
import com.nklcbdty.api.companyfeature.vo.FeatureType;

public interface CompanyFeatureRepository extends JpaRepository<CompanyFeature, Long> {

    List<CompanyFeature> findByFeatureType(FeatureType featureType);

    List<CompanyFeature> findByFeatureTypeAndFeatureCode(FeatureType featureType, String featureCode);

    List<CompanyFeature> findByFeatureTypeAndCompanyCdIn(FeatureType featureType, Collection<String> companyCds);
}
