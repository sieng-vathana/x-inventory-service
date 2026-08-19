package com.x.inventory.service;

import com.sharedlib.response.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class ProductClient {
    private final RestClient client;

    public ProductClient(RestClient.Builder builder,
                         @Value("${services.product.base-url:http://127.0.0.1:8082}") String baseUrl) {
        this.client = builder.clone()
                .requestInterceptor((request, body, execution) -> {
                    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                        String authorization = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
                        if (StringUtils.hasText(authorization)) {
                            request.getHeaders().set(HttpHeaders.AUTHORIZATION, authorization);
                        }
                    }
                    return execution.execute(request, body);
                })
                .baseUrl(baseUrl + "/api/v1/products")
                .build();
    }

    public VariantQuantity getVariantQuantity(Long variantId, Long storeId) {
        ApiResponse<VariantQuantity> response = client.get()
                .uri(uriBuilder -> uriBuilder.path("/variants/{id}")
                        .queryParam("storeId", storeId)
                        .build(variantId))
                .retrieve().body(new ParameterizedTypeReference<>() {});
        if (response == null || response.getData() == null) {
            throw new IllegalStateException("Product service returned no variant");
        }
        return response.getData();
    }

    public record VariantQuantity(Long storeId, Integer quantity) {}
}
