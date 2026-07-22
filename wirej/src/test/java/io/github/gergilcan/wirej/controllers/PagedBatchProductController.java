package io.github.gergilcan.wirej.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.gergilcan.wirej.annotations.ServiceClass;
import io.github.gergilcan.wirej.entities.Product;
import io.github.gergilcan.wirej.rest.PagedBatchController;
import io.github.gergilcan.wirej.services.PagedProductService;

@RestController
@RequestMapping("/products-paged-batch")
@ServiceClass(PagedProductService.class)
public interface PagedBatchProductController extends PagedBatchController<Product, Long> {
}
