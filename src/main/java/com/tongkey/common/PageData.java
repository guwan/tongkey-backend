package com.tongkey.common;

import org.springframework.data.domain.Page;

import java.util.List;

/** 统一分页响应。 */
public record PageData<T>(List<T> items, long total, int page, int size) {

    public static <T> PageData<T> of(Page<T> p) {
        return new PageData<>(p.getContent(), p.getTotalElements(), p.getNumber(), p.getSize());
    }

    public static <T, R> PageData<R> map(Page<T> p, java.util.function.Function<T, R> mapper) {
        return new PageData<>(p.getContent().stream().map(mapper).toList(), p.getTotalElements(), p.getNumber(), p.getSize());
    }
}
