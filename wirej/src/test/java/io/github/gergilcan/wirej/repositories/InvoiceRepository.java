package io.github.gergilcan.wirej.repositories;

import org.springframework.stereotype.Repository;

import io.github.gergilcan.wirej.entities.Invoice;
import io.github.gergilcan.wirej.repository.StandardRepository;

@Repository
public interface InvoiceRepository extends StandardRepository<Invoice, Long> {
}
