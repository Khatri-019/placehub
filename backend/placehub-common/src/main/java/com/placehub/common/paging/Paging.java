package com.placehub.common.paging;

import com.placehub.common.error.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Map;

/** Builds a PageRequest with clamped size and a whitelisted sort (unknown fields give 400). */
public final class Paging {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;

    private Paging() {
    }

    /**
     * @param sort          "field,asc|desc" or null
     * @param allowedFields API field name to entity property name
     */
    public static PageRequest of(Integer page, Integer size, String sort, Map<String, String> allowedFields,
                                 Sort defaultSort) {
        int p = page == null || page < 0 ? 0 : page;
        int s = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return PageRequest.of(p, s, parseSort(sort, allowedFields, defaultSort));
    }

    static Sort parseSort(String sort, Map<String, String> allowedFields, Sort defaultSort) {
        if (sort == null || sort.isBlank()) {
            return defaultSort;
        }
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        String property = allowedFields.get(field);
        if (property == null) {
            throw ApiException.badRequest("Unsupported sort field '" + field + "'. Allowed: "
                    + String.join(", ", allowedFields.keySet()));
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length > 1) {
            String d = parts[1].trim().toLowerCase();
            if (d.equals("desc")) {
                direction = Sort.Direction.DESC;
            } else if (!d.equals("asc")) {
                throw ApiException.badRequest("Sort direction must be 'asc' or 'desc'");
            }
        }
        return Sort.by(direction, property);
    }
}
