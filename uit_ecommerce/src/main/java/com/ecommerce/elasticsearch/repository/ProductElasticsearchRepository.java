package com.ecommerce.elasticsearch.repository;

import com.ecommerce.elasticsearch.document.ProductDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public interface ProductElasticsearchRepository extends ElasticsearchRepository<ProductDocument, Long> {
    
}
