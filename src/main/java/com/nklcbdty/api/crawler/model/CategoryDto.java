package com.nklcbdty.api.crawler.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDto {
    // Getter 및 Setter
    private String title;
    private List<String> categories;
    private boolean visible;
}
