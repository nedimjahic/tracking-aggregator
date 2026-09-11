package com.trackingaggregator.client.mapper;

import com.trackingaggregator.model.ShipmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class StatusMapperTest {

    private final StatusMapper mapper = new StatusMapper();

    @Nested
    class PerCourierCodes {

        @ParameterizedTest
        @CsvSource({
                "pre-transit, PICKED_UP",
                "transit, IN_TRANSIT",
                "out-for-delivery, OUT_FOR_DELIVERY",
                "delivered, DELIVERED",
                "failure, FAILED_ATTEMPT",
                // DHL is the only mapper that lowercases its input first
                "TRANSIT, IN_TRANSIT",
                "Transit, IN_TRANSIT",
                "DELIVERED, DELIVERED",
        })
        void mapsDhlCodes(String code, ShipmentStatus expected) {
            assertThat(mapper.mapDHL(code)).isEqualTo(expected);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"bogus", ""})
        void unknownDhlCodeIsUnknown(String code) {
            assertThat(mapper.mapDHL(code)).isEqualTo(ShipmentStatus.UNKNOWN);
        }

        @ParameterizedTest
        @CsvSource({
                "P, PICKED_UP",
                "I, IN_TRANSIT",
                "O, OUT_FOR_DELIVERY",
                "D, DELIVERED",
                "X, FAILED_ATTEMPT",
                "M, PICKED_UP",
        })
        void mapsUpsCodes(String code, ShipmentStatus expected) {
            assertThat(mapper.mapUPS(code)).isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource({
                "PU, PICKED_UP",
                "IT, IN_TRANSIT",
                "OD, OUT_FOR_DELIVERY",
                "DL, DELIVERED",
                "DE, FAILED_ATTEMPT",
                "SE, FAILED_ATTEMPT",
        })
        void mapsFedExCodes(String code, ShipmentStatus expected) {
            assertThat(mapper.mapFedEx(code)).isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource({
                "Pre-Shipment, PICKED_UP",
                "Accepted, PICKED_UP",
                "'In Transit', IN_TRANSIT",
                "'Out for Delivery', OUT_FOR_DELIVERY",
                "Delivered, DELIVERED",
        })
        void mapsUspsCategories(String category, ShipmentStatus expected) {
            assertThat(mapper.mapUSPS(category)).isEqualTo(expected);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"ZZ", ""})
        void unknownCodesAreUnknown(String code) {
            assertThat(mapper.mapUPS(code)).isEqualTo(ShipmentStatus.UNKNOWN);
            assertThat(mapper.mapFedEx(code)).isEqualTo(ShipmentStatus.UNKNOWN);
            assertThat(mapper.mapUSPS(code)).isEqualTo(ShipmentStatus.UNKNOWN);
        }

        @Test
        @DisplayName("UPS/FedEx/USPS lookups are case-sensitive while DHL is not")
        void caseSensitivityIsInconsistentAcrossCouriers() {
            // DHL lowercases its input; the other three do a raw map lookup. Pinned so
            // the asymmetry is a visible decision rather than an accident.
            assertThat(mapper.mapDHL("DELIVERED")).isEqualTo(ShipmentStatus.DELIVERED);
            assertThat(mapper.mapUPS("d")).isEqualTo(ShipmentStatus.UNKNOWN);
            assertThat(mapper.mapFedEx("dl")).isEqualTo(ShipmentStatus.UNKNOWN);
            assertThat(mapper.mapUSPS("in transit")).isEqualTo(ShipmentStatus.UNKNOWN);
        }
    }

    @Nested
    class FromDescription {

        @ParameterizedTest
        @CsvSource({
                "Delivered, DELIVERED",
                "DELIVERED, DELIVERED",
                "'Delivered to reception', DELIVERED",
                "'Out for delivery', OUT_FOR_DELIVERY",
                "'In transit', IN_TRANSIT",
                "'Arrived at transit hub', IN_TRANSIT",
                "'Picked up', PICKED_UP",
                "'pickup scan', PICKED_UP",
                "'Collected from sender', PICKED_UP",
                "'Returned to sender', RETURNED",
                "'Delivery exception', FAILED_ATTEMPT",
                "'Delivery failed', FAILED_ATTEMPT",
        })
        void mapsKeywords(String description, ShipmentStatus expected) {
            assertThat(mapper.mapFromDescription(description)).isEqualTo(expected);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "Label created"})
        void unrecognisedDescriptionIsUnknown(String description) {
            assertThat(mapper.mapFromDescription(description)).isEqualTo(ShipmentStatus.UNKNOWN);
        }

        @Test
        @DisplayName("a negated 'not delivered' is a failure, not a delivery")
        void notDeliveredIsAFailedAttempt() {
            // "not delivered" is checked as a failure indicator before "out for delivery"
            // and "delivered", so a negated description no longer reports DELIVERED.
            assertThat(mapper.mapFromDescription("Not delivered - out for delivery tomorrow"))
                    .isEqualTo(ShipmentStatus.FAILED_ATTEMPT);
        }

        @Test
        @DisplayName("'Delivered with exception' reports FAILED_ATTEMPT, not DELIVERED")
        void exceptionWinsOverDelivered() {
            assertThat(mapper.mapFromDescription("Delivered with exception"))
                    .isEqualTo(ShipmentStatus.FAILED_ATTEMPT);
        }

        @Test
        @DisplayName("'returned' is matched before 'failed'")
        void returnedWinsOverFailed() {
            assertThat(mapper.mapFromDescription("Returned after failed delivery"))
                    .isEqualTo(ShipmentStatus.RETURNED);
        }

        @Test
        @DisplayName("the 'in transit' clause is dead code - 'transit' already matched")
        void inTransitClauseIsRedundant() {
            // contains("transit") || contains("in transit") - the second operand can
            // never be reached. Harmless, but worth deleting.
            assertThat(mapper.mapFromDescription("transit")).isEqualTo(ShipmentStatus.IN_TRANSIT);
            assertThat(mapper.mapFromDescription("in transit")).isEqualTo(ShipmentStatus.IN_TRANSIT);
        }
    }
}
