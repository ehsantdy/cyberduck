package ch.cyberduck.core.editor;

/*
 * Copyright (c) 2013 David Kocher. All rights reserved.
 * http://cyberduck.ch/
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
 * dkocher@cyberduck.ch
 */

import ch.cyberduck.core.AbstractTestCase;
import ch.cyberduck.core.FactoryException;
import ch.cyberduck.core.local.LaunchServicesApplicationFinder;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @version $Id$
 */
public class MultipleEditorFactoryTest extends AbstractTestCase {

    @BeforeClass
    public static void register() {
        LaunchServicesApplicationFinder.register();
        MultipleEditorFactory.register();
    }

    @Test(expected = FactoryException.class)
    public void testEdit() {
        MultipleEditorFactory f = new MultipleEditorFactory();
        f.create();
    }
}
