package com.shop.catalog.internal.image.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.web.multipart.MultipartFile;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UploadProductImagesRequest {

    @NotEmpty(message = "PRODUCT_IMAGE_FILES_REQUIRED")
    List<MultipartFile> files = new ArrayList<>();

    Integer primaryIndex;
}
