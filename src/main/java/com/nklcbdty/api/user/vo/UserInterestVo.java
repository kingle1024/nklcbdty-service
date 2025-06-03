package com.nklcbdty.api.user.vo;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "user_interest")
public class UserInterestVo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id")
    private String userId;
    @Column(name = "item_type")
    private String itemType;
    @Column(name = "item_value")
    private String itemValue;
    @CreationTimestamp // 엔티티가 처음 저장될 때 현재 시간이 자동 설정됩니다.
    private LocalDateTime insertDts;
    @UpdateTimestamp // 엔티티가 업데이트될 때마다 현재 시간이 자동 설정됩니다.
    private LocalDateTime updateDts;
}
