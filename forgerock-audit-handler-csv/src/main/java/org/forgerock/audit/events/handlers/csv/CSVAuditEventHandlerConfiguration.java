/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2015 ForgeRock AS.
 */
package org.forgerock.audit.events.handlers.csv;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.forgerock.audit.events.handlers.EventHandlerConfiguration;
import org.forgerock.util.Reject;

/**
 * A configuration for CSV audit event handler.
 * <p>
 * This configuration object can be created from JSON. Example of valid JSON configuration:
 *
 * <pre>
 *  {
 *    "logDirectory" : "/tmp/audit",
 *    "csvConfiguration" : {
 *      "quoteChar" : ";"
 *    },
 *    "security" : {
 *      "enabled" : true,
 *      "filename" : "/var/secure/secure-audit.jks"
 *    }
 *  }
 * </pre>
 */
public class CSVAuditEventHandlerConfiguration extends EventHandlerConfiguration {

    @JsonProperty(required=true)
    @JsonPropertyDescription("audit.handlers.csv.logDirectory")
    private String logDirectory;

    @JsonPropertyDescription("audit.handlers.csv.formatting")
    private CsvFormatting formatting = new CsvFormatting();

    @JsonPropertyDescription("audit.handlers.csv.security")
    private CsvSecurity security = new CsvSecurity();

    /**
     * Returns the directory where CSV file is located.
     *
     * @return the location of the CSV file.
     */
    public String getLogDirectory() {
        return logDirectory;
    }

    /**
     * Sets the directory where CSV file is located.
     *
     * @param directory
     *            the directory.
     */
    public void setLogDirectory(String directory) {
        logDirectory = directory;
    }

    /**
     * Returns the CSV formatting options.
     *
     * @return the CSV formatting options.
     */
    public CsvFormatting getFormatting() {
        return formatting;
    }

    /**
     * Sets the CSV formatting options.
     *
     * @param formatting
     *            the CSV formatting options to set.
     */
    public void setFormatting(CsvFormatting formatting) {
        this.formatting = Reject.checkNotNull(formatting);
    }

    /**
     * Returns the CSV tamper evident options.
     *
     * @return the CSV tamper evident options.
     */
    public CsvSecurity getSecurity() {
        return security;
    }

    /**
     * Sets the CSV tamper evident options.
     *
     * @param security
     *            the CSV tamper evident options to set.
     */
    public void setSecurity(CsvSecurity security) {
        this.security = Reject.checkNotNull(security);
    }

    /**
     * Contains the csv writer configuration parameters
     */
    public static class CsvFormatting {
        @JsonPropertyDescription("audit.handlers.csv.formatting.quoteChar")
        private char quoteChar = '"';

        @JsonPropertyDescription("audit.handlers.csv.formatting.delimiterChar")
        private char delimiterChar = ',';

        @JsonPropertyDescription("audit.handlers.csv.formatting.endOfLineSymbols")
        private String endOfLineSymbols = System.getProperty("line.separator");

        /**
         * Gets the character to use to quote the csv entries.
         * @return The quote character.
         */
        public char getQuoteChar() {
            return quoteChar;
        }

        /**
         * Sets the character to use to quote the csv entries.
         * @param quoteChar The quote character.
         */
        public void setQuoteChar(char quoteChar) {
            this.quoteChar = quoteChar;
        }

        /**
         * Gets the character to use to delimit the csv entries.
         * @return The character used to delimit the entries.
         */
        public int getDelimiterChar() {
            return delimiterChar;
        }

        /**
         * Sets the character to use to delimit the csv entries.
         * @param delimiterChar The character used to delimit the entries.
         */
        public void setDelimiterChar(char delimiterChar) {
            this.delimiterChar = delimiterChar;
        }

        /**
         * Gets the end of line symbol.
         * @return The end of line symbol.
         */
        public String getEndOfLineSymbols() {
            return endOfLineSymbols;
        }

        /**
         * Gets the end of line symbol.
         * @param endOfLineSymbols The end of line symbol.
         */
        public void setEndOfLineSymbols(String endOfLineSymbols) {
            this.endOfLineSymbols = endOfLineSymbols;
        }
    }

    /**
     * Contains the configuration parameters to configure tamper evident logging.
     */
    public static class CsvSecurity {

        @JsonPropertyDescription("audit.handlers.csv.security.enabled")
        private boolean enabled = false;

        @JsonPropertyDescription("audit.handlers.csv.security.filename")
        private String filename;

        @JsonPropertyDescription("audit.handlers.csv.security.password")
        private String password;

        @JsonPropertyDescription("audit.handlers.csv.security.signatureInterval")
        private String signatureInterval;

        /**
         * Enables tamper evident logging. By default tamper evident logging is disabled.
         * @param enabled True - To enable tamper evident logging.
         *                False - To disable tamper evident logging.
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         *
         * Gets tamper evident logging enabled status. By default tamper evident logging is disabled.
         * @return True - If tamper evident logging enabled.
         *         False - If tamper evident logging disabled.
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Sets the location of the keystore to be used.
         * @param filename The location of the keystore.
         */
        public void setFilename(String filename) {
            this.filename = filename;
        }

        /**
         * Gets the location of the keystore to be used.
         * @return The location of the keystore.
         */
        public String getFilename() {
            return filename;
        }

        /**
         * Sets the password of the keystore.
         * @param password The password of the keystore.
         */
        public void setPassword(String password) {
            this.password = password;
        }

        /**
         * Gets the password of the keystore.
         * @return The password of the keystore.
         */
        public String getPassword() {
            return password;
        }

        /**
         * Sets the signature's interval.
         * @param signatureInterval The time's interval to insert periodically a signature.
         */
        public void setSignatureInterval(String signatureInterval) {
            this.signatureInterval = signatureInterval;
        }

        /**
         * Gets the signature's interval.
         * @return The time's interval to insert periodically a signature.
         */
        public String getSignatureInterval() {
            return signatureInterval;
        }

    }
}
