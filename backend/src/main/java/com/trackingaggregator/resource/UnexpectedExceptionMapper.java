package com.trackingaggregator.resource;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Catch-all for exceptions with no dedicated mapper, so an unexpected {@link RuntimeException}
 * (a bug, not a modelled failure) never falls through to the framework's default error handler,
 * which in dev/test mode renders the raw exception message and full stack trace in the response
 * body. JAX-RS picks the most specific registered mapper for a given exception type, so this
 * does not intercept {@link WebApplicationException} (Quarkus's own 404/405/etc. handling) or
 * bean-validation failures, both of which have more specific mappers of their own.
 */
@Provider
public class UnexpectedExceptionMapper implements ExceptionMapper<RuntimeException> {

    private static final Logger LOG = Logger.getLogger(UnexpectedExceptionMapper.class.getName());

    @Override
    public Response toResponse(RuntimeException e) {
        if (e instanceof WebApplicationException wae) {
            // Not our concern: Quarkus's own 404/405/406/etc. handling already carries the
            // correct Response. Some of these (e.g. NotFoundException, NotAcceptableException)
            // have no more specific provider registered, so without this check they would
            // otherwise be caught here as a plain RuntimeException.
            return wae.getResponse();
        }

        LOG.log(Level.SEVERE, "Unexpected error handling request", e);

        Map<String, Object> body = new HashMap<>();
        body.put("error", "INTERNAL_ERROR");
        body.put("message", "An unexpected error occurred");
        body.put("trackingNumber", null);

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }
}
