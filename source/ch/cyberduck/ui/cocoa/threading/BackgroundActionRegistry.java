package ch.cyberduck.ui.cocoa.threading;

/*
 *  Copyright (c) 2008 David Kocher. All rights reserved.
 *  http://cyberduck.ch/
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Bug fixes, suggestions and comments should be sent to:
 *  dkocher@cyberduck.ch
 */

import com.apple.cocoa.application.NSApplication;

import ch.cyberduck.core.Collection;

import org.apache.log4j.Logger;

/**
 * @version $Id:$
 */
public class BackgroundActionRegistry extends Collection {
    private static Logger log = Logger.getLogger(BackgroundActionRegistry.class);

    private static BackgroundActionRegistry instance;

    public static BackgroundActionRegistry instance() {
        synchronized(NSApplication.sharedApplication()) {
            if(null == instance) {
                instance = new BackgroundActionRegistry();
            }
            return instance;
        }
    }

    public boolean add(final Object action) {
        ((BackgroundAction)action).addListener(new BackgroundActionListener() {
            public void start(BackgroundAction action) {
                ;
            }

            public void stop(BackgroundAction action) {
                remove(action);
                synchronized(lock) {
                    lock.notify();
                }
            }
        });
        return super.add(action);
    }

    private BackgroundActionRegistry() {
        ;
    }

    private final Object lock = new Object();

    /**
     * Blocks the calling thread until all background actions have completed
     */
    public void block() {
        while(!this.isEmpty()) {
            synchronized(lock) {
                try {
                    log.info("Waiting for all background actions to complete...");
                    lock.wait();
                }
                catch(InterruptedException e) {
                    log.error(e.getMessage());
                }
            }
        }
        log.info("All background actions completed");
    }
}
