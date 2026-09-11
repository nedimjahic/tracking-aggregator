package com.trackingaggregator.service;

import com.trackingaggregator.model.Courier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Detection is a first-match-wins walk over an ordered LinkedHashMap, so the pattern
 * ORDER is part of the behaviour. Several of the tests below exist specifically to pin
 * that ordering down, because widening any one regex silently steals numbers from
 * whichever courier sits below it.
 */
class CourierDetectorServiceTest {

    private final CourierDetectorService detector = new CourierDetectorService();

    /** One sample per courier that is known to resolve to it. */
    private static Map<Courier, String> reachableSamples() {
        Map<Courier, String> samples = new LinkedHashMap<>();
        samples.put(Courier.UPS, "1Z999AA10123456784");   // 1Z + 16
        samples.put(Courier.FEDEX, "123456789012");       // 12 digits
        samples.put(Courier.USPS, "12345678901234567890"); // 20 digits
        samples.put(Courier.DHL, "1234567890");           // 10 digits
        samples.put(Courier.DPD, "12345678901234");       // 14 digits
        samples.put(Courier.GLS, "12345678901");          // 11 digits
        return samples;
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            // UPS: 1Z + 16 alphanumerics, case-insensitive
            "1Z999AA10123456784, UPS",
            "1z999aa10123456784, UPS",
            // FedEx: 12 or 15 digits
            "123456789012, FEDEX",
            "123456789012345, FEDEX",
            // USPS: 20-22 digits
            "12345678901234567890, USPS",
            "123456789012345678901, USPS",
            "1234567890123456789012, USPS",
            // DHL: 10 digits, or JJD + 18 digits
            "1234567890, DHL",
            "JJD000000000000000000, DHL",
            "jjd000000000000000000, DHL",
            // DPD: 14 digits
            "12345678901234, DPD",
            // GLS: 11 digits (12 is taken by FedEx, see below)
            "12345678901, GLS",
    })
    void detectsEachCourierFormat(String trackingNumber, Courier expected) {
        assertThat(detector.detect(trackingNumber)).contains(expected);
    }

    @Test
    @DisplayName("leading and trailing whitespace is trimmed")
    void trimsSurroundingWhitespace() {
        assertThat(detector.detect("  1Z999AA10123456784  ")).contains(Courier.UPS);
    }

    @Test
    @DisplayName("12 digits resolve to FedEx - the GLS pattern is shadowed")
    void twelveDigits_resolveToFedEx_glsIsShadowed() {
        // "123456789012" matches BOTH ^\d{12}(\d{3})?$ (FedEx) and ^\d{11,12}$ (GLS).
        // FedEx is inserted into the LinkedHashMap first, so FedEx wins and GLS is
        // reachable only via 11-digit numbers. Pinned deliberately: if the insertion
        // order ever changes, every 12-digit GLS parcel silently routes to FedEx.
        assertThat(detector.detect("123456789012")).contains(Courier.FEDEX);
    }

    @Test
    @DisplayName("14 digits resolve to DPD - no other pattern covers that length")
    void fourteenDigits_resolveToDpd() {
        // FedEx is 12 or 15, GLS 11-12, USPS 20-22, DHL 10 - so 14 is unambiguous today.
        // This breaks the moment someone widens the FedEx or GLS pattern.
        assertThat(detector.detect("12345678901234")).contains(Courier.DPD);
    }

    @ParameterizedTest
    @EnumSource(Courier.class)
    @DisplayName("every courier in the enum is reachable by at least one tracking number")
    void everyCourierIsReachable(Courier courier) {
        String sample = reachableSamples().get(courier);
        assertThat(sample)
                .as("no sample tracking number defined for %s", courier)
                .isNotNull();
        assertThat(detector.detect(sample))
                .as("%s is unreachable - a pattern above it now shadows %s", courier, sample)
                .contains(courier);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void returnsEmptyForNullOrBlank(String trackingNumber) {
        assertThat(detector.detect(trackingNumber)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1234567890123",          // 13 digits - coverage gap, see below
            "1234567890123456",       // 16
            "12345678901234567",      // 17
            "123456789012345678",     // 18
            "1234567890123456789",    // 19
            "12345678901234567890123", // 23
            "1Z999AA1012345678",      // 1Z + 15, one short
            "1Z999AA101234567845",    // 1Z + 17, one long
            "1Z999AA1012345678!",     // illegal character
            "JJD00000000000000000",   // JJD + 17 digits
            "1Z-999-AA1-0123-456-784", // punctuation
    })
    void returnsEmptyForUnrecognisedFormats(String trackingNumber) {
        assertThat(detector.detect(trackingNumber)).isEmpty();
    }

    @Test
    @DisplayName("13-digit numbers are unmatched - known coverage gap")
    void thirteenDigits_areUnmatched_knownGap() {
        // No pattern covers 13 digits, so 13-char USPS / UPS Mail Innovations formats
        // and S10 codes like "EA123456789US" are not detectable at all today.
        assertThat(detector.detect("1234567890123")).isEmpty();
        assertThat(detector.detect("EA123456789US")).isEmpty();
    }

    @Test
    @DisplayName("internal whitespace is not stripped - known limitation")
    void internalWhitespace_isNotStripped_knownLimitation() {
        // TODO: whitespace normalization must be applied consistently to detection, the
        // cache key and the courier call if it's ever added.
        Optional<Courier> result = detector.detect("1Z 999 AA1 0123 456 784");
        assertThat(result).isEmpty();
    }
}
