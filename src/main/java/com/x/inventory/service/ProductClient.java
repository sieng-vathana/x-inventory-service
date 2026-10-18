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

    /** Names and SKU of a variant sold in the store, kept on purchase lines as they were when ordered. */
    public VariantDetails getVariantDetails(Long variantId, Long storeId) {
        ApiResponse<VariantDetails> response = client.get()
                .uri(uriBuilder -> uriBuilder.path("/variants/{id}")
                        .queryParam("storeId", storeId)
                        .build(variantId))
                .retrieve().body(new ParameterizedTypeReference<>() {});
        if (response == null || response.getData() == null) {
            throw new IllegalStateException("Product service returned no variant");
        }
        return response.getData();
    }

    /** A supplier of the business; the product service answers 404 for another business's supplier. */
    public SupplierSummary getSupplier(Long supplierId, Long businessId) {
        ApiResponse<SupplierSummary> response = client.get()
                .uri(uriBuilder -> uriBuilder.path("/suppliers/{id}")
                        .queryParam("businessId", businessId)
                        .build(supplierId))
                .retrieve().body(new ParameterizedTypeReference<>() {});
        if (response == null || response.getData() == null) {
            throw new IllegalStateException("Product service returned no supplier");
        }
        return response.getData();
    }

    public record VariantDetails(Long variantId, Long storeId, String productName, String variantName, String sku) {}

    public record SupplierSummary(Long id, String supplierName, String status) {}
}
