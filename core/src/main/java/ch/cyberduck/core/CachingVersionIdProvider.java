package ch.cyberduck.core;

/*
 * Copyright (c) 2002-2022 iterate GmbH. All rights reserved.
 * https://cyberduck.io/
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

import ch.cyberduck.core.cache.LRUCache;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.features.VersionIdProvider;
import ch.cyberduck.core.preferences.PreferencesFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class CachingVersionIdProvider implements VersionIdProvider {
    private static final Logger log = LogManager.getLogger(CachingVersionIdProvider.class);

    private final LRUCache<SimplePathPredicate, String> cache
            = LRUCache.build(PreferencesFactory.get().getLong("fileid.cache.size"));

    private final Protocol.Case sensitivity;

    protected CachingVersionIdProvider(final Protocol.Case sensitivity) {
        this.sensitivity = sensitivity;
    }

    @Override
    public String getVersionId(final Path file, final ListProgressListener listener) throws BackgroundException {
        return cache.get(this.toPredicate(file));
    }

    private SimplePathPredicate toPredicate(final Path file) {
        return sensitivity == Protocol.Case.sensitive ? new CaseSensitivePathPredicate(file) : new CaseInsensitivePathPredicate(file);
    }

    /**
     * Cache version identifier
     *
     * @param file Remote path
     * @param id   Null to remove from cache
     * @return Input parameter
     */
    public String cache(final Path file, final String id) {
        if(file.attributes().isDuplicate()) {
            log.warn(String.format("Skip caching for previous version %s", file));
            return id;
        }
        if(log.isDebugEnabled()) {
            log.debug(String.format("Cache %s for file %s", id, file));
        }
        if(null == id) {
            cache.remove(this.toPredicate(file));
            file.attributes().setVersionId(null);
            if(file.isDirectory()) {
                for(SimplePathPredicate entry : cache.asMap().keySet()) {
                    if(entry.isChild(this.toPredicate(file))) {
                        cache.remove(entry);
                    }
                }
            }
        }
        else {
            cache.put(this.toPredicate(file), id);
            file.attributes().setVersionId(id);
        }
        return id;
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
