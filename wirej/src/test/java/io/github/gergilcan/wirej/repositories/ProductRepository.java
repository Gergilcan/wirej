package io.github.gergilcan.wirej.repositories;

import org.springframework.stereotype.Repository;

import io.github.gergilcan.wirej.annotations.QueryFile;
import io.github.gergilcan.wirej.entities.Product;
import io.github.gergilcan.wirej.repository.StandardRepository;

// Deliberately mixes inherited StandardRepository CRUD with a hand-written
// @QueryFile method, to prove both styles coexist in one generated impl.
@Repository
public interface ProductRepository extends StandardRepository<Product, Long> {
    @QueryFile("/queries/Product/countProducts.sql")
    Long countProducts();
}
