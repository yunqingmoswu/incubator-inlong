/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.inlong.sort.base.dirty;

import org.apache.inlong.sort.base.util.PatternReplaceUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Dirty data base class, it is a wrapper of dirty data
 *
 * @param <T>
 */
public class DirtyData<T> {

    private static final String DIRTY_TYPE_KEY = "DIRTY_TYPE";

    private static final String SYSTEM_TIME_KEY = "SYSTEM_TIME";

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * The labels of the dirty data, it will be written to store system of dirty
     */
    private final String labels;
    /**
     * The log tag of dirty data, it is only used to format log as follows:
     * [${logTag}] ${labels} ${data}
     */
    private final String logTag;
    /**
     * Dirty type
     */
    private final DirtyType dirtyType;
    /**
     * The real dirty data
     */
    private final T data;

    public DirtyData(T data, String labels, String logTag, DirtyType dirtyType) {
        this.data = data;
        this.dirtyType = dirtyType;
        Map<String, String> paramMap = genParamMap();
        this.labels = PatternReplaceUtils.replace(labels, paramMap);
        this.logTag = PatternReplaceUtils.replace(logTag, paramMap);
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    private Map<String, String> genParamMap() {
        Map<String, String> paramMap = new HashMap<>();
        paramMap.put(SYSTEM_TIME_KEY, DATE_TIME_FORMAT.format(LocalDateTime.now()));
        paramMap.put(DIRTY_TYPE_KEY, dirtyType.format());
        return paramMap;
    }

    public String getLabels() {
        return labels;
    }

    public String getLogTag() {
        return logTag;
    }

    public T getData() {
        return data;
    }

    public DirtyType getDirtyType() {
        return dirtyType;
    }

    public static class Builder<T> {

        private String labels;
        private String logTag;
        private DirtyType dirtyType = DirtyType.UNDEFINED;
        private T data;

        public Builder<T> setDirtyType(DirtyType dirtyType) {
            this.dirtyType = dirtyType;
            return this;
        }

        public Builder<T> setLabels(String labels) {
            this.labels = labels;
            return this;
        }

        public Builder<T> setData(T data) {
            this.data = data;
            return this;
        }

        public Builder<T> setLogTag(String logTag) {
            this.logTag = logTag;
            return this;
        }

        public DirtyData<T> build() {
            return new DirtyData<>(data, labels, logTag, dirtyType);
        }
    }
}
