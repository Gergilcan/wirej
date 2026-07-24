package io.github.gergilcan.wirej.rest;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import io.github.gergilcan.wirej.annotations.ServiceMethod;
import io.github.gergilcan.wirej.core.RequestFilters;
import io.github.gergilcan.wirej.core.RequestPagination;

/**
 * Same standard REST CRUD surface as {@link StandardRestController}, except
 * {@code getAll} is meant to be backed by a service method returning a
 * {@code PagedResult<T>} (page of data plus total count) instead of a plain
 * array - pair this with {@link io.github.gergilcan.wirej.repository.PagedRepository}
 * on the repository side, whose {@code getAll} already returns exactly that.
 *
 * The controller method itself is declared {@code ResponseEntity<?>}, same
 * as {@code StandardRestController} - nothing here enforces the body shape,
 * this interface exists purely so the intent (paged list responses) is
 * explicit at the type level instead of being an implementation detail of
 * whichever service backs it.
 */
public interface PagedController<T, ID> {
  @GetMapping("/{id}")
  @ServiceMethod
  @ResponseStatus(HttpStatus.OK)
  ResponseEntity<?> get(@PathVariable("id") ID id);

  @GetMapping("/")
  @ServiceMethod
  @ResponseStatus(HttpStatus.OK)
  ResponseEntity<?> getAll(RequestFilters filters, RequestPagination pagination);

  @PostMapping("/")
  @ServiceMethod
  @ResponseStatus(HttpStatus.CREATED)
  ResponseEntity<?> create(@RequestBody T entity);

  @PutMapping("/{id}")
  @ServiceMethod
  @ResponseStatus(HttpStatus.OK)
  ResponseEntity<?> update(@PathVariable("id") ID id, @RequestBody T entity);

  @PatchMapping("/{id}")
  @ServiceMethod
  @ResponseStatus(HttpStatus.OK)
  ResponseEntity<?> patch(@PathVariable("id") ID id, @RequestBody Map<String, Object> changes);

  @DeleteMapping("/{id}")
  @ServiceMethod
  @ResponseStatus(HttpStatus.NO_CONTENT)
  ResponseEntity<?> delete(@PathVariable("id") ID id);
}
