package ch.cyberduck.core.threading;

/*
 * Copyright (c) 2002-2017 iterate GmbH. All rights reserved.
 * https://cyberduck.io/
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
 */

import ch.cyberduck.core.AbstractController;
import ch.cyberduck.core.Controller;

import org.apache.commons.lang3.concurrent.ConcurrentUtils;
import org.apache.log4j.Logger;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

public class DefaultBackgroundExecutor implements BackgroundExecutor {
    private static final Logger log = Logger.getLogger(AbstractController.class);

    private static final DefaultBackgroundExecutor DEFAULT = new DefaultBackgroundExecutor();

    public static BackgroundExecutor get() {
        return DEFAULT;
    }

    private final ThreadPool singleExecutor;
    private final ThreadPool concurrentExecutor;

    protected DefaultBackgroundExecutor() {
        this(new LoggingUncaughtExceptionHandler());
    }

    protected DefaultBackgroundExecutor(final Thread.UncaughtExceptionHandler handler) {
        singleExecutor = ThreadPoolFactory.get(1, handler);
        concurrentExecutor = ThreadPoolFactory.get(handler);
    }

    @Override
    public <T> Future<T> execute(final Controller controller, final BackgroundActionRegistry registry, final BackgroundAction<T> action) {
        if(log.isDebugEnabled()) {
            log.debug(String.format("Run action %s in background", action));
        }
        if(registry.contains(action)) {
            log.warn(String.format("Skip duplicate background action %s found in registry", action));
            return ConcurrentUtils.constantFuture(null);
        }
        registry.add(action);
        action.init();
        // Start background task
        final Callable<T> command = new BackgroundCallable<T>(action, controller, registry);
        try {
            final Future<T> task;
            if(null == action.lock()) {
                task = concurrentExecutor.execute(command);
            }
            else {
                if(log.isDebugEnabled()) {
                    log.debug(String.format("Synchronize on lock %s for action %s", action.lock(), action));
                }
                task = singleExecutor.execute(command);
            }
            if(log.isInfoEnabled()) {
                log.info(String.format("Scheduled background runnable %s for execution", action));
            }
            return task;
        }
        catch(RejectedExecutionException e) {
            log.error(String.format("Error scheduling background task %s for execution. %s", action, e.getMessage()));
            action.cleanup();
            return ConcurrentUtils.constantFuture(null);
        }
    }

    @Override
    public void shutdown() {
        if(log.isInfoEnabled()) {
            log.info(String.format("Terminating single executor thread pool %s", singleExecutor));
        }
        singleExecutor.shutdown(false);
        if(log.isInfoEnabled()) {
            log.info(String.format("Terminating concurrent executor thread pool %s", concurrentExecutor));
        }
        concurrentExecutor.shutdown(false);
    }
}
