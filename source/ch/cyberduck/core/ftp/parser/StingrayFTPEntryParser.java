package ch.cyberduck.core.ftp.parser;

/*
 *  Copyright (c) 2007 David Kocher. All rights reserved.
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

import java.text.ParseException;

import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.parser.ConfigurableFTPFileEntryParserImpl;

/**
 * @see org.apache.commons.net.ftp.parser.UnixFTPEntryParser
 * @version $Id: StingrayFTPEntryParser.java 3171 2007-07-16 10:30:28Z dkocher $
 */
public class StingrayFTPEntryParser extends ConfigurableFTPFileEntryParserImpl {

    static final String DEFAULT_DATE_FORMAT
            = "MMM d yyyy"; //Nov 9 2001

    static final String DEFAULT_RECENT_DATE_FORMAT
            = "MMM d HH:mm"; //Nov 9 20:06

    static final String NUMERIC_DATE_FORMAT
            = "yyyy-MM-dd HH:mm"; //2001-11-09 20:06

    /**
     * Some Linux distributions are now shipping an FTP server which formats
     * file listing dates in an all-numeric format:
     * <code>"yyyy-MM-dd HH:mm</code>.
     * This is a very welcome development,  and hopefully it will soon become
     * the standard.  However, since it is so new, for now, and possibly
     * forever, we merely accomodate it, but do not make it the default.
     * <p/>
     * For now end users may specify this format only via
     * <code>UnixFTPEntryParser(FTPClientConfig)</code>.
     * Steve Cohen - 2005-04-17
     */
    public static final FTPClientConfig NUMERIC_DATE_CONFIG =
            new FTPClientConfig(
                    FTPClientConfig.SYST_UNIX,
                    NUMERIC_DATE_FORMAT,
                    null, null, null, null);

    /**
     * this is the regular expression used by this parser.
     * <p/>
     * Permissions:
     * r   the file is readable
     * w   the file is writable
     * x   the file is executable
     * -   the indicated permission is not granted
     * L   mandatory locking occurs during access (the set-group-ID bit is
     * on and the group execution bit is off)
     * s   the set-user-ID or set-group-ID bit is on, and the corresponding
     * user or group execution bit is also on
     * S   undefined bit-state (the set-user-ID bit is on and the user
     * execution bit is off)
     * t   the 1000 (octal) bit, or sticky bit, is on [see chmod(1)], and
     * execution is on
     * T   the 1000 bit is turned on, and execution is off (undefined bit-
     * state)
     */
    private static final String REGEX =
            "([bcdlfmpSs-])"
                    + "(((r|-)(w|-)([xsStTL-]))((r|-)(w|-)([xsStTL-]))((r|-)(w|-)([xsStTL-])))\\+?\\s+"
                    + "(\\d+)?\\s+"
                    + "(\\d+)?\\s+"
                    + "(\\S+)\\s+"
                    + "(\\d+)\\s+"

                    /*
                      numeric or standard format date
                    */
                    + "((?:\\d+[-/]\\d+[-/]\\d+)|(?:\\S+\\s+\\S+))\\s+"

                    /*
                year (for non-recent standard format)
                or time (for numeric or recent standard format
             */
                    + "(\\d+(?::\\d+)?)\\s+"

                    + "(\\S*)(\\s*.*)";

    /**
     * The default constructor for a UnixFTPEntryParser object.
     *
     * @throws IllegalArgumentException Thrown if the regular expression is unparseable.  Should not be seen
     *                                  under normal conditions.  It it is seen, this is a sign that
     *                                  <code>REGEX</code> is  not a valid regular expression.
     */
    public StingrayFTPEntryParser() {
        this(null);
    }

    /**
     * This constructor allows the creation of a UnixFTPEntryParser object with
     * something other than the default configuration.
     *
     * @param config The {@link FTPClientConfig configuration} object used to
     *               configure this parser.
     * @throws IllegalArgumentException Thrown if the regular expression is unparseable.  Should not be seen
     *                                  under normal conditions.  It it is seen, this is a sign that
     *                                  <code>REGEX</code> is  not a valid regular expression.
     * @since 1.4
     */
    public StingrayFTPEntryParser(FTPClientConfig config) {
        super(REGEX);
        configure(config);
    }


    /**
     * Parses a line of a unix (standard) FTP server file listing and converts
     * it into a usable format in the form of an <code> FTPFile </code>
     * instance.  If the file listing line doesn't describe a file,
     * <code> null </code> is returned, otherwise a <code> FTPFile </code>
     * instance representing the files in the directory is returned.
     * <p/>
     *
     * @param entry A line of text from the file listing
     * @return An FTPFile instance corresponding to the supplied entry
     */
    public FTPFile parseFTPEntry(String entry) {
        FTPFile file = new FTPFile();
        file.setRawListing(entry);
        int type;
        boolean isDevice = false;

        if (matches(entry)) {
            String typeStr = group(1);
            String filesize = group(18);
            String datestr = group(19) + " " + group(20);
            String name = group(21);
            String endtoken = group(22);

            try {
                file.setTimestamp(super.parseTimestamp(datestr));
            }
            catch (ParseException e) {
                return null;  // this is a parsing failure too.
            }

            // bcdlfmpSs-
            switch (typeStr.charAt(0)) {
                case 'd':
                    type = FTPFile.DIRECTORY_TYPE;
                    break;
                case 'l':
                    type = FTPFile.SYMBOLIC_LINK_TYPE;
                    break;
                case 'b':
                case 'c':
                    isDevice = true;
                    // break; - fall through
                case 'f':
                case '-':
                    type = FTPFile.FILE_TYPE;
                    break;
                default:
                    type = FTPFile.UNKNOWN_TYPE;
            }

            file.setType(type);

            int g = 4;
            for (int access = 0; access < 3; access++, g += 4) {
                // Use != '-' to avoid having to check for suid and sticky bits
                file.setPermission(access, FTPFile.READ_PERMISSION,
                        (!group(g).equals("-")));
                file.setPermission(access, FTPFile.WRITE_PERMISSION,
                        (!group(g + 1).equals("-")));

                String execPerm = group(g + 2);
                if (!execPerm.equals("-") && !Character.isUpperCase(execPerm.charAt(0))) {
                    file.setPermission(access, FTPFile.EXECUTE_PERMISSION, true);
                } else {
                    file.setPermission(access, FTPFile.EXECUTE_PERMISSION, false);
                }
            }

            try {
                file.setSize(Long.parseLong(filesize));
            }
            catch (NumberFormatException e) {
                // intentionally do nothing
            }

            if (null == endtoken) {
                file.setName(name);
            } else {
                // oddball cases like symbolic links, file names
                // with spaces in them.
                name += endtoken;
                if (type == FTPFile.SYMBOLIC_LINK_TYPE) {

                    int end = name.indexOf(" -> ");
                    // Give up if no link indicator is present
                    if (end == -1) {
                        file.setName(name);
                    } else {
                        file.setName(name.substring(0, end));
                        file.setLink(name.substring(end + 4));
                    }

                } else {
                    file.setName(name);
                }
            }
            return file;
        }
        return null;
    }

    /**
     * Defines a default configuration to be used when this class is
     * instantiated without a {@link  FTPClientConfig  FTPClientConfig}
     * parameter being specified.
     *
     * @return the default configuration for this parser.
     */
    protected FTPClientConfig getDefaultConfiguration() {
        return new FTPClientConfig(
                FTPClientConfig.SYST_UNIX,
                DEFAULT_DATE_FORMAT,
                DEFAULT_RECENT_DATE_FORMAT,
                null, null, null);
    }
}
