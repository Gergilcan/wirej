package io.github.gergilcan.wirej.core;

import java.util.Map;

/**
 * One entry of a batch PATCH request: which row to update, and the fields to
 * change on it. A {@code List} rather than an array on the receiving end,
 * since {@code new BatchPatchItem<ID>[n]} is not legal Java (generic array
 * creation).
 */
public record BatchPatchItem<ID>(ID id, Map<String, Object> changes) {
}
