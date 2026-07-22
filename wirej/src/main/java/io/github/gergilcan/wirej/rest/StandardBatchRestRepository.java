package io.github.gergilcan.wirej.rest;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import io.github.gergilcan.wirej.annotations.ServiceMethod;
import io.github.gergilcan.wirej.core.RequestFilters;
import io.github.gergilcan.wirej.core.RequestPagination;

/**
 * Same standard REST CRUD surface as {@link StandardRestRepository}, except
 * {@code create}/{@code patch} accept either a single JSON object or a JSON
 * array on the same route, batching the array case. A separate interface
 * rather than a {@code StandardRestRepository} extension: the two can't
 * share a route (an inherited and a redeclared method would both map
 * {@code @PostMapping("/")}, which Spring rejects as an ambiguous mapping at
 * startup), and existing services backing plain {@code StandardRestRepository}
 * controllers shouldn't be forced to grow batch overloads they don't want.
 *
 * The service class needs TWO overloads per batch-capable method, resolved
 * by ordinary Java overloading: {@code create(T)}/{@code create(T[])}, and
 * {@code patch(ID, Map<String,Object>)}/{@code patch(List<BatchPatchItem<ID>>)}.
 * Both are required - there's no silent single-only fallback.
 *
 * {@code patch} takes no path variable: since a batch body can't carry one id
 * per path segment, every entry (the single object, or each array element)
 * is a flat JSON object carrying its own {@code "id"} key alongside the
 * fields to change, e.g. {@code {"id": 5, "name": "New Name"}}. This is a
 * fixed HTTP-layer convention, independent of the entity's actual primary
 * key field/column name on the repository side.
 */
public interface StandardBatchRestRepository<T, ID> {
  @GetMapping("/{id}")
  @ServiceMethod
  @ResponseStatus(HttpStatus.OK)
  ResponseEntity<?> get(@PathVariable("id") ID id);

  @GetMapping("/")
  @ServiceMethod
  @ResponseStatus(HttpStatus.OK)
  ResponseEntity<?> getAll(RequestFilters filters, RequestPagination pagination);

  @PostMapping("/")
  @ServiceMethod(batchSupported = true)
  @ResponseStatus(HttpStatus.CREATED)
  ResponseEntity<?> create(@RequestBody JsonNode body);

  @PatchMapping("/")
  @ServiceMethod(batchSupported = true)
  @ResponseStatus(HttpStatus.OK)
  ResponseEntity<?> patch(@RequestBody JsonNode body);

  @DeleteMapping("/{id}")
  @ServiceMethod
  @ResponseStatus(HttpStatus.NO_CONTENT)
  ResponseEntity<?> delete(@PathVariable("id") ID id);
}
