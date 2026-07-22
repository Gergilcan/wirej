package io.github.gergilcan.wirej.entities;

import com.fasterxml.jackson.annotation.JsonAlias;

import io.github.gergilcan.wirej.annotations.WireJId;
import io.github.gergilcan.wirej.annotations.WireJTable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

// Exercises the explicit WireJ annotations and a primary key that is neither
// named 'id' nor mapped to a same-named column: @WireJTable/@WireJId take
// precedence over the JPA annotations, and @JsonAlias maps the field to its
// snake_case column for both generated SQL and result-set mapping.
@Entity
@Table(name = "invoices")
@WireJTable("invoices")
@Data
public class Invoice {
    @Id
    @WireJId
    @JsonAlias("invoice_number")
    private Long invoiceNumber;

    private String description;
}
