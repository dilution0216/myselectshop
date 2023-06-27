package com.sparta.myselectshop.repository;

import com.sparta.myselectshop.entity.Product;
import com.sparta.myselectshop.entity.ProductFolder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductClassRepository extends JpaRepository<ProductFolder, Long> {
}
