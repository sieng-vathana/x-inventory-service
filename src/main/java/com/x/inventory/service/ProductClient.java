package com.x.inventory.service;

import com.sharedlib.response.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ProductClient {
    private final RestClient client;

    public ProductClient(RestClient.Builder builder,
                         @Value("${services.product.base-url:http://127.0.0.1:8082}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl + "/api/v1/products").build();
    }

    public ProductVariantStock getVariant(Long variantId) {
        ApiResponse<ProductVariantStock> response = client.get()
                .uri("/variants/{id}", variantId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        if (response == null || response.getData() == null) {
            throw new IllegalStateException("Product service returned no variant");
        }
        return response.getData();
    }
}
