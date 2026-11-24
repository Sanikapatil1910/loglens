package com.loglens.dto;

/** A name with an occurrence count, used for "most frequent" statistics and chart data. */
public record FrequentItem(String name, long count) {
}
