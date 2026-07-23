package io.github.gergilcan.wirej;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.github.gergilcan.wirej.core.PagedResult;
import io.github.gergilcan.wirej.core.RequestFilters;
import io.github.gergilcan.wirej.core.RequestPagination;
import io.github.gergilcan.wirej.entities.Invoice;
import io.github.gergilcan.wirej.entities.Product;
import io.github.gergilcan.wirej.exceptions.WireJException;
import io.github.gergilcan.wirej.repositories.InvoiceRepository;
import io.github.gergilcan.wirej.repositories.PagedProductRepository;
import io.github.gergilcan.wirej.repositories.ProductRepository;

/**
 * Exercises the generated implementation of a repository that inherits its
 * CRUD surface from StandardRepository<Product, Long> - SQL for these
 * operations is generated at compile time from the entity's fields, no .sql
 * files exist for them. The hand-written @QueryFile method on the same
 * interface proves both styles coexist in one generated impl.
 */
@SpringBootTest(classes = TestApplication.class)
class StandardRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private PagedProductRepository pagedProductRepository;

    private Product newProduct(long id, String name) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        return product;
    }

    private Invoice newInvoice(long invoiceNumber, String description) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setDescription(description);
        return invoice;
    }

    @Test
    void fullCrudLifecycleThroughGeneratedSql() {
        Product created = productRepository.create(newProduct(1001L, "Widget"));
        assertThat(created.getName()).isEqualTo("Widget");

        Product fetched = productRepository.get(1001L);
        assertThat(fetched).isNotNull();
        assertThat(fetched.getId()).isEqualTo(1001L);
        assertThat(fetched.getName()).isEqualTo("Widget");

        Product updated = productRepository.update(1001L, Map.of("name", "Updated Widget"));
        assertThat(updated.getName()).isEqualTo("Updated Widget");

        Product[] all = productRepository.getAll(new RequestFilters(null, null, "id==DESC"));
        assertThat(all).isNotEmpty();

        productRepository.delete(1001L);
        assertThat(productRepository.get(1001L)).isNull();
    }

    @Test
    void getAllRespectsFiltersAndPagination() {
        productRepository.create(newProduct(1101L, "Alpha"));
        productRepository.create(newProduct(1102L, "Beta"));
        productRepository.create(newProduct(1103L, "Gamma"));

        Product[] filtered = productRepository.getAll(new RequestFilters("name==Beta", null, "id==DESC"));
        assertThat(filtered).hasSize(1);
        assertThat(filtered[0].getName()).isEqualTo("Beta");

        Product[] page = productRepository.getAll(new RequestFilters(null, null, "id==DESC"));
        assertThat(page).hasSize(3);
    }

    @Test
    void mixedInterfaceAlsoServesItsHandWrittenQueryFileMethod() {
        productRepository.create(newProduct(1201L, "Counted"));

        Long count = productRepository.countProducts();
        assertThat(count).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void updateRejectsUnknownFieldsSoArbitraryKeysNeverReachTheSql() {
        productRepository.create(newProduct(1301L, "Guarded"));

        WireJException ex = assertThrows(WireJException.class,
                () -> productRepository.update(1301L, Map.of("name = 'x' WHERE 1=1; --", "evil")));
        assertThat(ex.getMessage()).contains("Unknown field in update payload");
    }

    @Test
    void updateRejectsThePrimaryKeyAsAChangeableField() {
        productRepository.create(newProduct(1401L, "Pinned"));

        WireJException ex = assertThrows(WireJException.class,
                () -> productRepository.update(1401L, Map.of("id", 9999L)));
        assertThat(ex.getMessage()).contains("Unknown field in update payload");
    }

    @Test
    void updateRejectsAnEmptyChangesMap() {
        WireJException ex = assertThrows(WireJException.class,
                () -> productRepository.update(1L, Map.of()));
        assertThat(ex.getMessage()).contains("at least one field");
    }

    @Test
    void crudLifecycleWorksWithAWireJIdAnnotatedPrimaryKeyNotNamedId() {
        invoiceRepository.create(newInvoice(2001L, "First Invoice"));

        Invoice fetched = invoiceRepository.get(2001L);
        assertThat(fetched).isNotNull();
        assertThat(fetched.getInvoiceNumber()).isEqualTo(2001L);
        assertThat(fetched.getDescription()).isEqualTo("First Invoice");

        Invoice updated = invoiceRepository.update(2001L, Map.of("description", "Amended Invoice"));
        assertThat(updated.getDescription()).isEqualTo("Amended Invoice");
        assertThat(updated.getInvoiceNumber()).isEqualTo(2001L);

        invoiceRepository.delete(2001L);
        assertThat(invoiceRepository.get(2001L)).isNull();
    }

    @Test
    void updateRejectsTheCustomPrimaryKeyUnderBothItsFieldNameAndItsColumnAlias() {
        invoiceRepository.create(newInvoice(2101L, "Locked"));

        assertThrows(WireJException.class,
                () -> invoiceRepository.update(2101L, Map.of("invoiceNumber", 9999L)));
        assertThrows(WireJException.class,
                () -> invoiceRepository.update(2101L, Map.of("invoice_number", 9999L)));
    }

    @Test
    void countReflectsFiltersIndependentlyOfPagination() {
        productRepository.create(newProduct(1501L, "Countable"));
        productRepository.create(newProduct(1502L, "Countable"));
        productRepository.create(newProduct(1503L, "Countable"));
        productRepository.create(newProduct(1504L, "Different"));

        Long matching = productRepository.count(new RequestFilters("name==Countable;id>=1501;id<=1504", null, null));
        assertThat(matching).isEqualTo(3L);

        Long all = productRepository.count(new RequestFilters("id>=1501;id<=1504", null, null));
        assertThat(all).isEqualTo(4L);
    }

    @Test
    void getPageReturnsThePageOfDataAlongsideTheUnpaginatedTotalCount() {
        pagedProductRepository.create(newProduct(1601L, "Paged"));
        pagedProductRepository.create(newProduct(1602L, "Paged"));
        pagedProductRepository.create(newProduct(1603L, "Paged"));
        pagedProductRepository.create(newProduct(1604L, "Paged"));
        pagedProductRepository.create(newProduct(1605L, "Not Paged"));

        RequestFilters filters = new RequestFilters("name==Paged;id>=1601;id<=1605", null, "id==ASC");

        PagedResult<Product> firstPage = pagedProductRepository.getAll(filters, new RequestPagination(0, 2));
        assertThat(firstPage.getTotalCount()).isEqualTo(4L);
        assertThat(Arrays.stream(firstPage.getData()).map(Product::getId)).containsExactly(1601L, 1602L);

        PagedResult<Product> secondPage = pagedProductRepository.getAll(filters, new RequestPagination(1, 2));
        assertThat(secondPage.getTotalCount()).isEqualTo(4L);
        assertThat(Arrays.stream(secondPage.getData()).map(Product::getId)).containsExactly(1603L, 1604L);
    }

    @Test
    void pagedRepositoryGetAllReturnsPagedResultDirectlyInsteadOfNeedingASeparateGetPageCall() {
        pagedProductRepository.create(newProduct(1701L, "PagedRepo"));
        pagedProductRepository.create(newProduct(1702L, "PagedRepo"));
        pagedProductRepository.create(newProduct(1703L, "PagedRepo"));

        RequestFilters filters = new RequestFilters("name==PagedRepo;id>=1701;id<=1703", null, "id==ASC");

        PagedResult<Product> firstPage = pagedProductRepository.getAll(filters, new RequestPagination(0, 2));
        assertThat(firstPage.getTotalCount()).isEqualTo(3L);
        assertThat(Arrays.stream(firstPage.getData()).map(Product::getId)).containsExactly(1701L, 1702L);

        PagedResult<Product> secondPage = pagedProductRepository.getAll(filters, new RequestPagination(1, 2));
        assertThat(secondPage.getTotalCount()).isEqualTo(3L);
        assertThat(Arrays.stream(secondPage.getData()).map(Product::getId)).containsExactly(1703L);

        Product updated = pagedProductRepository.update(1701L, Map.of("name", "Updated PagedRepo"));
        assertThat(updated.getName()).isEqualTo("Updated PagedRepo");

        pagedProductRepository.delete(1701L);
        assertThat(pagedProductRepository.get(1701L)).isNull();
    }
}
