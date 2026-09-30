package com.shop.catalog.internal.image.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ArrangeProductImagesRequest {

    @NotEmpty(message = "PRODUCT_IMAGE_ARRANGEMENT_INVALID")
    @Builder.Default
    List<@NotNull(message = "PRODUCT_IMAGE_ARRANGEMENT_INVALID") UUID> imageIds = new ArrayList<>();

    @NotNull(message = "PRODUCT_IMAGE_ARRANGEMENT_INVALID")
    UUID primaryImageId;
}
