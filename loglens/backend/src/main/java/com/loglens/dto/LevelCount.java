package com.loglens.dto;

import com.loglens.model.LogLevel;

/**
 * @param percentage share of log records (entries plus unrecognised lines), rounded to 2 decimals.
 *                   Stack-trace continuation lines belong to their entry and are not counted separately.
 */
public record LevelCount(LogLevel level, long count, double percentage) {
}
