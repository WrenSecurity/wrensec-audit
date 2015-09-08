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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import javax.crypto.spec.SecretKeySpec;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class HmacCalculatorTest {

    File keystoreFile;

    @BeforeMethod
    private void setup() throws IOException {
        keystoreFile = new File("target", "test-secure-audit.jks");
        tearDown();
    }

    @AfterMethod
    private void tearDown() throws IOException {
        Files.deleteIfExists(keystoreFile.toPath());
    }

    @Test
    public void shouldCalculateHMAC() throws Exception {
        HmacCalculator hmacCalculator = spy(new HmacCalculator(new SecretKeySpec("forgerock".getBytes("UTF-8"), "HmacSHA256"), "HmacSHA256"));

        byte[] data = "rockstar".getBytes("UTF-8");
        String base64HMAC;

        base64HMAC = hmacCalculator.calculate(data);
        assertThat(base64HMAC).isEqualTo("m9Yir8xEvAS4leA6IZGJQ++OGkyMMHnf334N94AlVIs=");

        // The key is updated after each generated HMAC so the HMAC for the same data must not be the same.
        base64HMAC = hmacCalculator.calculate(data);
        assertThat(base64HMAC).isEqualTo("xDSrLEfpe3V1t5Y9y2I4bCIUIdWcQKum9HiVDHXfHFQ=");
    }

}
