package com.trackingaggregator.resource;

import com.trackingaggregator.client.CourierApiException;
import com.trackingaggregator.model.Courier;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CourierApiExceptionMapperTest {

    private final CourierApiExceptionMapper mapper = new CourierApiExceptionMapper();

    @Test
    void mapsToServiceUnavailableWithCourierDetail() {
        CourierApiException e =
                new CourierApiException(Courier.DHL, "1234567890", 500, new RuntimeException("upstream"));

        Response response = mapper.toResponse(e);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getMediaType()).isEqualTo(MediaType.APPLICATION_JSON_TYPE);

        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) response.getEntity();
        assertThat(entity)
                .containsEntry("error", "COURIER_UNAVAILABLE")
                .containsEntry("trackingNumber", "1234567890");
        assertThat((String) entity.get("message")).contains("DHL");
    }

    @Test
    @DisplayName("a null tracking number still maps to a 503")
    void nullTrackingNumberDoesNotThrow() {
        CourierApiException e =
                new CourierApiException(Courier.DHL, null, 500, new RuntimeException("upstream"));

        Response response = mapper.toResponse(e);

        assertThat(response.getStatus()).isEqualTo(503);
        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) response.getEntity();
        assertThat(entity)
                .containsEntry("error", "COURIER_UNAVAILABLE")
                .containsEntry("trackingNumber", null);
    }

    @Test
    @DisplayName("a null courier renders literally in the message but does not throw")
    void nullCourierRendersInMessage() {
        CourierApiException e =
                new CourierApiException(null, "1234567890", 500, new RuntimeException("upstream"));

        Response response = mapper.toResponse(e);

        assertThat(response.getStatus()).isEqualTo(503);
        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) response.getEntity();
        assertThat((String) entity.get("message")).isEqualTo("Courier null is temporarily unavailable");
    }
}
