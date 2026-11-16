package com.conkord.saigt.dto.core;

import java.util.List;

/** Envelope de paginação, formato exato de §22.3. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
