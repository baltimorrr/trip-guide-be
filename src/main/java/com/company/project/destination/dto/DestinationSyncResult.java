package com.company.project.destination.dto;

/**
 * Outcome of one destination sync.
 *
 * @param created places added by this run
 * @param withHotels live destinations that now have at least one listed hotel
 */
public record DestinationSyncResult(int created, int withHotels) {
}
