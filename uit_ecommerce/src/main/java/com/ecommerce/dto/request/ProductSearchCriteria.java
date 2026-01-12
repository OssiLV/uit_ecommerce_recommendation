package com.ecommerce.dto.request;

import java.math.BigDecimal;

public record ProductSearchCriteria(String keyword,
                                    Long categoryId,
                                    BigDecimal minPrice,
                                    BigDecimal maxPrice) {
}
