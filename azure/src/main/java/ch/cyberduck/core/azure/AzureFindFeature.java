package ch.cyberduck.core.azure;

/*
 * Copyright (c) 2002-2014 David Kocher. All rights reserved.
 * http://cyberduck.io/
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * Bug fixes, suggestions and comments should be sent to:
 * feedback@cyberduck.io
 */

import ch.cyberduck.core.DirectoryDelimiterPathContainerService;
import ch.cyberduck.core.ListProgressListener;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.PathContainerService;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.exception.NotfoundException;
import ch.cyberduck.core.features.Find;

import com.azure.core.exception.HttpResponseException;

public class AzureFindFeature implements Find {

    private final AzureSession session;
    private final PathContainerService containerService
        = new DirectoryDelimiterPathContainerService();

    public AzureFindFeature(final AzureSession session) {
        this.session = session;
    }

    @Override
    public boolean find(Path file, final ListProgressListener listener) throws BackgroundException {
        if(file.isRoot()) {
            return true;
        }
        try {
            try {
                final boolean found;
                if(containerService.isContainer(file)) {
                    return session.getClient().getBlobContainerClient(containerService.getContainer(file).getName()).exists();
                }
                return session.getClient().getBlobContainerClient(containerService.getContainer(file).getName())
                    .getBlobClient(containerService.getKey(file)).exists();
            }
            catch(HttpResponseException e) {
                throw new AzureExceptionMappingService().map("Failure to read attributes of {0}", e, file);
            }
        }
        catch(NotfoundException e) {
            return false;
        }
    }
}
