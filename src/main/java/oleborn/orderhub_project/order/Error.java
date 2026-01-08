package oleborn.orderhub_project.order;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Error(
        int status,
        String code,
        Object details
) {
}
