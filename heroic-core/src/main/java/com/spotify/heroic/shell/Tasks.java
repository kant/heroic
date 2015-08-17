/*
 * Copyright (c) 2015 Spotify AB.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.spotify.heroic.shell;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.Chronology;
import org.joda.time.DateTime;
import org.joda.time.chrono.ISOChronology;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeParser;
import org.joda.time.format.DateTimeParserBucket;

import com.spotify.heroic.common.DateRange;
import com.spotify.heroic.common.RangeFilter;
import com.spotify.heroic.filter.Filter;
import com.spotify.heroic.filter.FilterFactory;
import com.spotify.heroic.grammar.QueryParser;

public final class Tasks {
    public static Filter setupFilter(FilterFactory filters, QueryParser parser, QueryParams params) {
        final List<String> query = params.getQuery();

        if (query.isEmpty())
            return filters.t();

        return parser.parseFilter(StringUtils.join(query, " "));
    }

    public abstract static class QueryParamsBase extends AbstractShellTaskParams implements QueryParams {
        private final DateRange defaultDateRange;

        public QueryParamsBase() {
            final long now = System.currentTimeMillis();
            final long start = now - TimeUnit.MILLISECONDS.convert(7, TimeUnit.DAYS);
            this.defaultDateRange = new DateRange(start, now);
        }

        @Override
        public DateRange getRange() {
            return defaultDateRange;
        }
    }

    public static RangeFilter setupRangeFilter(FilterFactory filters, QueryParser parser, QueryParams params) {
        final Filter filter = setupFilter(filters, parser, params);
        return new RangeFilter(filter, params.getRange(), params.getLimit());
    }

    private static final List<DateTimeParser> today = new ArrayList<>();
    private static final List<DateTimeParser> full = new ArrayList<>();

    static {
        today.add(DateTimeFormat.forPattern("HH:mm").getParser());
        today.add(DateTimeFormat.forPattern("HH:mm:ss").getParser());
        today.add(DateTimeFormat.forPattern("HH:mm:ss.SSS").getParser());
        full.add(DateTimeFormat.forPattern("yyyy-MM-dd/HH:mm").getParser());
        full.add(DateTimeFormat.forPattern("yyyy-MM-dd/HH:mm:ss").getParser());
        full.add(DateTimeFormat.forPattern("yyyy-MM-dd/HH:mm:ss.SSS").getParser());
    }

    public static long parseInstant(String input, long now) {
        if (input.charAt(0) == '+') {
            return now + Long.parseLong(input.substring(1));
        }

        if (input.charAt(0) == '-') {
            return now - Long.parseLong(input.substring(1));
        }

        // try to parse just milliseconds
        try {
            return Long.valueOf(input);
        } catch (IllegalArgumentException e) {
            // pass-through
        }

        final Chronology chrono = ISOChronology.getInstanceUTC();

        if (input.indexOf('/') >= 0) {
            return parseFullInstant(input, chrono);
        }

        return parseTodayInstant(input, chrono, now);
    }

    private static long parseTodayInstant(String input, final Chronology chrono, long now) {
        final DateTime n = new DateTime(now, chrono);

        for (final DateTimeParser p : today) {
            final DateTimeParserBucket bucket = new DateTimeParserBucket(0, chrono, null, null);

            bucket.saveField(chrono.year(), n.getYear());
            bucket.saveField(chrono.monthOfYear(), n.getMonthOfYear());
            bucket.saveField(chrono.dayOfYear(), n.getDayOfYear());

            try {
                p.parseInto(bucket, input, 0);
            } catch (IllegalArgumentException e) {
                // pass-through
                continue;
            }

            return bucket.computeMillis();
        }

        throw new IllegalArgumentException(input + " is not a valid instant");
    }

    private static long parseFullInstant(String input, final Chronology chrono) {
        for (final DateTimeParser p : full) {
            final DateTimeParserBucket bucket = new DateTimeParserBucket(0, chrono, null, null);

            try {
                p.parseInto(bucket, input, 0);
            } catch (IllegalArgumentException e) {
                // pass-through
                continue;
            }

            return bucket.computeMillis();
        }

        throw new IllegalArgumentException(input + " is not a valid instant");
    }

    public static String formatTimeNanos(long diff) {
        if (diff < 1000) {
            return String.format("%d ns", diff);
        }

        if (diff < 1000000) {
            final double v = ((double) diff) / 1000;
            return String.format("%.3f us", v);
        }

        if (diff < 1000000000) {
            final double v = ((double) diff) / 1000000;
            return String.format("%.3f ms", v);
        }

        final double v = ((double) diff) / 1000000000;
        return String.format("%.3f s", v);
    }

    public static interface QueryParams {
        public List<String> getQuery();

        public DateRange getRange();

        public int getLimit();
    }

    public static interface ElasticSearchParams {
        public String getSeeds();

        public String getClusterName();

        public String getBackendType();
    }
}