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

package org.apache.inlong.sort.protocol;

import lombok.Getter;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Locale;

/**
 * Meta field info.
 */
@Getter
public class MetaFieldInfo extends FieldInfo {

    private static final long serialVersionUID = -3436204467879205139L;

    @JsonProperty("metaField")
    private final MetaField metaField;

    @JsonCreator
    public MetaFieldInfo(
            @JsonProperty("name") String name,
            @JsonProperty("nodeId") String nodeId,
            @JsonProperty("metaField") MetaField metaField) {
        super(name, nodeId, null);
        this.metaField = metaField;
    }

    public MetaFieldInfo(
            @JsonProperty("name") String name,
            @JsonProperty("metaField") MetaField metaField) {
        super(name);
        this.metaField = metaField;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        MetaFieldInfo that = (MetaFieldInfo) o;
        return metaField == that.metaField
                && super.equals(that);
    }

    public enum MetaField {

        /**
         * The process time of flink
         */
        PROCESS_TIME,
        /**
         * Name of the schema that contain the row, currently used for Oracle database
         */
        SCHEMA_NAME,
        /**
         * Name of the database that contain the row.
         */
        DATABASE_NAME,
        /**
         * Name of the table that contain the row.
         */
        TABLE_NAME,
        /**
         * It indicates the time that the change was made in the database.
         * If the record is read from snapshot of the table instead of the change stream, the value is always 0
         */
        OP_TS,
        /**
         * Whether the DDL statement. Currently, it is used for MySQL database.
         */
        IS_DDL,
        /**
         * Type of database operation, such as INSERT/DELETE, etc. Currently, it is used for MySQL database.
         */
        OP_TYPE,
        /**
         * MySQL binlog data Row. Currently, it is used for MySQL database.
         */
        DATA,
        /**
         * The value of the field before update. Currently, it is used for MySQL database.
         */
        UPDATE_BEFORE,
        /**
         * Batch id of binlog. Currently, it is used for MySQL database.
         */
        BATCH_ID,
        /**
         * Mapping of sql_type table fields to java data type IDs. Currently, it is used for MySQL database.
         */
        SQL_TYPE,
        /**
         * The current time when the ROW was received and processed. Currently, it is used for MySQL database.
         */
        TS,
        /**
         * The table structure. It is only used for MySQL database
         */
        MYSQL_TYPE,
        /**
         * Primary key field name. Currently, it is used for MySQL database.
         */
        PK_NAMES;

        public static MetaField forName(String name) {
            for (MetaField metaField : values()) {
                if (metaField.name().equals(name.toUpperCase(Locale.ROOT))) {
                    return metaField;
                }
            }
            throw new UnsupportedOperationException(String.format("Unsupported MetaField=%s for Inlong", name));
        }
    }
}
