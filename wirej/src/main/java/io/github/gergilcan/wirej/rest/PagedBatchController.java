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
 * {@link PagedController} + {@link StandardBatchRestRepository}'s batch
 * create/patch: {@code getAll} is meant to be backed by a service method
 * returning {@code PagedResult<T>}, and {@code create}/{@code patch} accept
 * either a single JSON object or a JSON array on the same route. See both of
 * those for the rationale behind each half of this interface.
 */
public interface PagedBatchController<T, ID> {
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
