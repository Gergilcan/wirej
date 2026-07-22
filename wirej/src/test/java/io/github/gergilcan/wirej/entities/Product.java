package io.github.gergilcan.wirej.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

// No WireJ-specific annotations: the processor resolves the table from the
// JPA @Table(name=...) and the primary key from the JPA @Id, so entities
// already annotated for schema tooling need nothing extra.
@Entity
@Table(name = "products")
@Data
public class Product {
    @Id
    private Long id;

    private String name;

    private Double price;
}
