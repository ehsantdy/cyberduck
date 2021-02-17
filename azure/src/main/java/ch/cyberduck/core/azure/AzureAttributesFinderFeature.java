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

import ch.cyberduck.core.Path;
import ch.cyberduck.core.PathAttributes;
import ch.cyberduck.core.PathContainerService;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.features.AttributesFinder;
import ch.cyberduck.core.io.Checksum;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;

import java.util.HashMap;
import java.util.Map;

import com.azure.core.exception.HttpResponseException;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobContainerProperties;
import com.azure.storage.blob.models.BlobItemProperties;
import com.azure.storage.blob.models.BlobProperties;

public class AzureAttributesFinderFeature implements AttributesFinder {

    private final AzureSession session;

    private final PathContainerService containerService
        = new AzurePathContainerService();

    public static final String KEY_BLOB_TYPE = "blob_type";

    public AzureAttributesFinderFeature(final AzureSession session) {
        this.session = session;
    }

    @Override
    public PathAttributes find(final Path file) throws BackgroundException {
        if(file.isRoot()) {
            return PathAttributes.EMPTY;
        }
        try {
            if(containerService.isContainer(file)) {
                final PathAttributes attributes = new PathAttributes();
                final BlobContainerClient client = session.getClient().getBlobContainerClient(containerService.getContainer(file).getName());
                final BlobContainerProperties properties = client.getProperties();
                attributes.setETag(properties.getETag());
                attributes.setModificationDate(properties.getLastModified().toInstant().toEpochMilli());
                return attributes;
            }
            else {
                final BlobProperties properties = session.getClient().getBlobContainerClient(containerService.getContainer(file).getName())
                    .getBlobClient(containerService.getKey(file)).getBlockBlobClient().getProperties();
                return this.toAttributes(properties);
            }
        }
        catch(HttpResponseException e) {
            throw new AzureExceptionMappingService().map("Failure to read attributes of {0}", e, file);
        }
    }

    protected PathAttributes toAttributes(final BlobProperties properties) {
        final PathAttributes attributes = new PathAttributes();
        attributes.setSize(properties.getBlobSize());
        attributes.setModificationDate(properties.getLastModified().toInstant().toEpochMilli());
        if(properties.getContentMd5() != null) {
            attributes.setChecksum(Checksum.parse(Hex.encodeHexString(Base64.decodeBase64(properties.getContentMd5()))));
        }
        attributes.setETag(properties.getETag());
        final Map<String, String> custom = new HashMap<>();
        custom.put(AzureAttributesFinderFeature.KEY_BLOB_TYPE, properties.getBlobType().name());
        attributes.setCustom(custom);
        return attributes;
    }

    protected PathAttributes toAttributes(final BlobItemProperties properties) {
        final PathAttributes attributes = new PathAttributes();
        attributes.setSize(properties.getContentLength());
        attributes.setModificationDate(properties.getLastModified().toInstant().toEpochMilli());
        attributes.setETag(properties.getETag());
        if(properties.getContentMd5() != null) {
            attributes.setChecksum(Checksum.parse(Hex.encodeHexString(Base64.decodeBase64(properties.getContentMd5()))));
        }
        return attributes;
    }
}
