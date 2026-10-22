package com.company.project.destination.services;

import com.company.project.destination.dto.DestinationSyncResult;

/**
 * Builds destinations from the addresses of listed hotels and refreshes their hotel counts.
 * Never changes the fields admins own (display name, image, featured rank). Safe to run at any time.
 */
public interface DestinationSyncService {

	DestinationSyncResult sync();

}
