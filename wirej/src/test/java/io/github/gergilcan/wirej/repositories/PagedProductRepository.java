package io.github.gergilcan.wirej.repositories;

import org.springframework.stereotype.Repository;

import io.github.gergilcan.wirej.entities.Product;
import io.github.gergilcan.wirej.repository.PagedRepository;

@Repository
public interface PagedProductRepository extends PagedRepository<Product, Long> {
}
