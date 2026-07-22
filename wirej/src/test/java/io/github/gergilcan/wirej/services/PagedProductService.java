package io.github.gergilcan.wirej.services;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import io.github.gergilcan.wirej.core.BatchPatchItem;
import io.github.gergilcan.wirej.core.PagedResult;
import io.github.gergilcan.wirej.core.RequestFilters;
import io.github.gergilcan.wirej.core.RequestPagination;
import io.github.gergilcan.wirej.entities.Product;
import io.github.gergilcan.wirej.repositories.PagedProductRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PagedProductService {
    private final PagedProductRepository repository;

    public Product get(Long id) {
        return repository.get(id);
    }

    public PagedResult<Product> getAll(RequestFilters filters, RequestPagination pagination) {
        return repository.getAll(filters, pagination);
    }

    public Product create(Product entity) {
        return repository.create(entity);
    }

    public Product[] create(Product[] entities) {
        return repository.createBatch(entities);
    }

    public Product patch(Long id, Map<String, Object> changes) {
        return repository.update(id, changes);
    }

    public Product[] patch(List<BatchPatchItem<Long>> items) {
        return repository.updateBatch(items);
    }

    public void delete(Long id) {
        repository.delete(id);
    }
}
