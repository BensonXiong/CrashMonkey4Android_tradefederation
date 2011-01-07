/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tradefed.command;

import com.android.ddmlib.Log;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.util.QuotationAwareTokenizer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for file that contains set of command lines.
 * <p/>
 * The syntax of the given file should be series of lines. Each line is one configuration plus its
 * options, delimited by whitespace:
 * <pre>
 *   [options] config-name
 *   [options] config-name2
 *   ...
 * </pre>
 */
class CommandFileParser {
    private static final String LOG_TAG = "CommandFileParser";

    /**
     * A pattern that matches valid macro usages and captures the name of the macro.
     * Macro names must start with an alpha character, and may contain alphanumerics, underscores,
     * or hyphens.
     */
    private static final Pattern mMacroPattern = Pattern.compile("([a-z][a-z0-9_-]*)\\(\\)",
            Pattern.CASE_INSENSITIVE);

    private Map<String, ConfigLine> mMacros = new HashMap<String, ConfigLine>();
    private Map<String, List<ConfigLine>> mLongMacros = new HashMap<String, List<ConfigLine>>();
    private List<ConfigLine> mLines = new LinkedList<ConfigLine>();

    @SuppressWarnings("serial")
    private class ConfigLine extends LinkedList<String> {
        ConfigLine() {
            super();
        }

        ConfigLine(Collection<? extends String> c) {
            super(c);
        }
    }

    /**
     * Checks if a line matches the expected format for a (short) macro:
     * MACRO (name) = (token) [(token)...]
     * This method verifies that:
     * <ol>
     *   <li>Line is at least four tokens long</li>
     *   <li>The first token is "MACRO" (case-sensitive)</li>
     *   <li>The third token is an equal-sign</li>
     * </ol>
     *
     * @return {@code true} if the line matches the macro format, {@false} otherwise
     */
    private static boolean isLineMacro(ConfigLine line) {
        return line.size() >= 4 && "MACRO".equals(line.get(0)) && "=".equals(line.get(2));
    }

    /**
     * Checks if a line matches the expected format for the opening line of a long macro:
     * LONG MACRO (name)
     *
     * @return {@code true} if the line matches the long macro format, {@code false} otherwise
     */
    private static boolean isLineLongMacro(ConfigLine line) {
        return line.size() == 3 && "LONG".equals(line.get(0)) && "MACRO".equals(line.get(1));
    }

    /**
     * Checks if a line matches the expected format for an INCLUDE directive
     *
     * @return {@code true} if the line is an INCLUDE directive, {@code false} otherwise
     */
    private static boolean isLineIncludeDirective(ConfigLine line) {
        return line.size() == 2 && "INCLUDE".equals(line.get(0));
    }

    /**
     * Checks if a line should be parsed or ignored.  Basically, ignore if the line is commented
     * or is empty.
     *
     * @param line A {@see String} containing the line of input to check
     * @return {@code true} if we should parse the line, {@code false} if we should ignore it.
     */
    private static boolean shouldParseLine(String line) {
        line = line.trim();
        return !(line.isEmpty() || line.startsWith("#"));
    }

    /**
     * Does a single pass of the input CommandFile, storing input lines as macros, long macros, or
     * configuration lines.
     *
     * Note that this method may call itself recursively to handle the INCLUDE directive.
     */
    private void scanFile(File file) throws IOException, ConfigurationException {
        BufferedReader fileReader = createCommandFileReader(file);
        String inputLine = null;
        try {
            while ((inputLine = fileReader.readLine()) != null) {
                inputLine = inputLine.trim();
                if (shouldParseLine(inputLine)) {
                    ConfigLine lArgs = null;
                    try {
                        String[] args = QuotationAwareTokenizer.tokenizeLine(inputLine);
                        lArgs = new ConfigLine(Arrays.asList(args));
                    } catch (IllegalArgumentException e) {
                        throw new ConfigurationException(e.getMessage());
                    }

                    if (isLineMacro(lArgs)) {
                        // Expected format: MACRO <name> = <token> [<token>...]
                        String name = lArgs.get(1);
                        ConfigLine expansion = new ConfigLine(lArgs.subList(3, lArgs.size()));
                        mMacros.put(name, expansion);
                    } else if (isLineLongMacro(lArgs)) {
                        // Expected format: LONG MACRO <name>\n(multiline expansion)\nEND MACRO
                        String name = lArgs.get(2);
                        List<ConfigLine> expansion = new LinkedList<ConfigLine>();

                        inputLine = fileReader.readLine();
                        while (!"END MACRO".equals(inputLine)) {
                            if (inputLine == null) {
                                // Syntax error
                                throw new ConfigurationException(String.format(
                                        "Syntax error: Unexpected EOF while reading definition " +
                                        "for LONG MACRO %s.", name));
                            }
                            if (shouldParseLine(inputLine)) {
                                // Store the tokenized line
                                ConfigLine line = new ConfigLine(Arrays.asList(
                                        QuotationAwareTokenizer.tokenizeLine(inputLine)));
                                expansion.add(line);
                            }

                            // Advance
                            inputLine = fileReader.readLine();
                        }
                        Log.d(LOG_TAG, String.format("Parsed %d-line definition for long macro %s",
                                expansion.size(), name));

                        mLongMacros.put(name, expansion);
                    } else if (isLineIncludeDirective(lArgs)) {
                        Log.d(LOG_TAG, String.format("Got an include directive for file %s",
                                lArgs.get(1)));
                        scanFile(new File(lArgs.get(1)));
                    } else {
                        mLines.add(lArgs);
                    }
                }
            }
        } finally {
            fileReader.close();
        }
    }

    /**
     * Parses the configs contained in {@code file}, doing macro expansions as necessary, and adds
     * them to {@code scheduler}.
     *
     * @param file the {@link File} to parse
     * @param scheduler the {@link ICommandScheduler} to add configs to
     * @throws IOException if failed to read file
     * @throws ConfigurationException if content of file could not be parsed
     */
    public void parseFile(File file, ICommandScheduler scheduler) throws IOException,
            ConfigurationException {
        scanFile(file);

        // Now perform macro expansion
        /**
         * inputBitmask is used to stop iterating when we're sure there are no more macros to
         * expand.  It is a bitmask where the (k)th bit represents the (k)th element in
         * {@code mLines.}
         * <p>
         * Each bit starts as {@code true}, meaning that each line in mLines may have macro calls to
         * be expanded.  We set bits of {@code inputBitmask} to {@code false} once we've determined
         * that the corresponding lines of {@code mLines} have been fully expanded, which allows us
         * to skip those lines on subsequent scans.
         * <p>
         * {@code inputBitmaskCount} stores the quantity of {@code true} bits in
         * {@code inputBitmask}.  Once {@code inputBitmaskCount == 0}, we are done expanding macros.
         */
        List<Boolean> inputBitmask = new LinkedList<Boolean>();
        for (int i=0; i < mLines.size(); ++i) {
            // true == this element may need to be expanded
            inputBitmask.add(true);
        }
        int inputBitmaskCount = mLines.size();

        // Do a maximum of 10 iterations of expansion
        // FIXME: make this configurable
        for (int iCount = 0; iCount < 10 && inputBitmaskCount > 0; ++iCount) {
            Log.d(LOG_TAG, "### Expansion iteration " + iCount);

            int inputIdx = 0;
            while (inputIdx < mLines.size()) {
                if (!inputBitmask.get(inputIdx)) {
                    // Skip this line; we've already determined that it doesn't contain any macro
                    // calls to be expanded.
                    ++inputIdx;
                    continue;
                }

                ConfigLine line = mLines.get(inputIdx);
                boolean sawMacro = expandMacro(line);
                List<ConfigLine> longMacroExpansion = expandLongMacro(line, !sawMacro);

                if (longMacroExpansion == null) {
                    if (sawMacro) {
                        // We saw and expanded a short macro.  This may have pulled in another macro
                        // to expand, so leave inputBitmask alone.
                    } else {
                        // We did not find any macros (long or short) to expand, thus all expansions
                        // are done for this ConfigLine.  Update inputBitmask appropriately.
                        inputBitmask.set(inputIdx, false);
                        --inputBitmaskCount;
                    }

                    // Finally, advance.
                    ++inputIdx;
                } else {
                    // We expanded a long macro.  First, actually insert the expansion in place of
                    // the macro call
                    mLines.remove(inputIdx);
                    mLines.addAll(inputIdx, longMacroExpansion);

                    // Now update the bitmask to keep it in sync with mLines.  Since each of the
                    // added lines may contain a macro call, we set the new values to (true)
                    for (int i = 0; i < longMacroExpansion.size(); ++i) {
                        inputBitmask.add(inputIdx, true);
                    }

                    // And advance past the end of the expanded macro
                    inputIdx += longMacroExpansion.size();
                }
            }
        }

        for (ConfigLine configLine : mLines) {
            Log.d(LOG_TAG, String.format("Adding line: %s", configLine.toString()));
            String[] aryCmdLine = new String[configLine.size()];
            scheduler.addConfig(configLine.toArray(aryCmdLine));
        }
    }

    /**
     * Performs one level of macro expansion for the first macro used in the line
     */
    private List<ConfigLine> expandLongMacro(ConfigLine line, boolean checkMissingMacro)
            throws ConfigurationException {
        for (int idx = 0; idx < line.size(); ++idx) {
            String token = line.get(idx);
            Matcher matchMacro = mMacroPattern.matcher(token);
            if (matchMacro.matches()) {
                // we hit a macro; expand it
                List<ConfigLine> expansion = new LinkedList<ConfigLine>();
                String name = matchMacro.group(1);
                List<ConfigLine> longMacro = mLongMacros.get(name);
                if (longMacro == null) {
                    if (checkMissingMacro) {
                        // If the expandMacro method hits an unrecognized macro, it will leave it in
                        // the stream for this method.  If it's not recognized here, throw an
                        // exception
                        throw new ConfigurationException(String.format(
                                "Macro call '%s' does not match any macro definitions.", name));
                    } else {
                        return null;
                    }
                }

                ConfigLine prefix = new ConfigLine(line.subList(0, idx));
                ConfigLine suffix = new ConfigLine(line.subList(idx, line.size()));
                suffix.remove(0);
                for (ConfigLine macroLine : longMacro) {
                    ConfigLine expanded = new ConfigLine();
                    expanded.addAll(prefix);
                    expanded.addAll(macroLine);
                    expanded.addAll(suffix);
                    expansion.add(expanded);
                }

                // Only expand a single macro usage at a time
                return expansion;
            }
        }
        return null;
    }

    /**
     * Performs one level of macro expansion for every macro used in the line
     *
     * @return {@code true} if a macro was found and expanded, {@code false} if no macro was found
     */
    private boolean expandMacro(ConfigLine line) {
        boolean sawMacro = false;

        int idx = 0;
        while (idx < line.size()) {
            String token = line.get(idx);
            Matcher matchMacro = mMacroPattern.matcher(token);
            if (matchMacro.matches() && mMacros.containsKey(matchMacro.group(1))) {
                // we hit a macro; expand it
                String name = matchMacro.group(1);
                ConfigLine macro = mMacros.get(name);
                Log.d(LOG_TAG, String.format("Gotcha!  Expanding macro '%s' to '%s'", name, macro));
                line.remove(idx);
                line.addAll(idx, macro);
                idx += macro.size();
                sawMacro = true;
            } else {
                ++idx;
            }
        }
        return sawMacro;
    }

    /**
     * Create a reader for the command file data.
     * <p/>
     * Exposed for unit testing.
     *
     * @param file the command {@link File}
     * @return the {@link BufferedReader}
     * @throws IOException if failed to read data
     */
    BufferedReader createCommandFileReader(File file) throws IOException {
        return new BufferedReader(new FileReader(file));
    }
}