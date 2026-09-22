package info.isaksson.erland.survey.surveyapi;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<ApiException> {
    @Override
    public Response toResponse(ApiException exception) {
        return Response.status(exception.status)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("code", exception.code, "message", exception.getMessage()))
                .build();
    }
}
