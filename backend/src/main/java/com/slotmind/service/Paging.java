package com.slotmind.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** The one pagination rule: page default 0, size default 20, max 50 (clamped). */
public final class Paging {

	public static final int DEFAULT_SIZE = 20;

	public static final int MAX_SIZE = 50;

	private Paging() {
	}

	public static Pageable of(Integer page, Integer size, Sort sort) {
		int p = page == null ? 0 : page;
		int s = size == null ? DEFAULT_SIZE : size;
		if (p < 0) {
			throw new BadRequestException("Invalid request parameter", List.of("page: must be >= 0"));
		}
		if (s < 1) {
			throw new BadRequestException("Invalid request parameter", List.of("size: must be >= 1"));
		}
		return PageRequest.of(p, Math.min(s, MAX_SIZE), sort);
	}

}
