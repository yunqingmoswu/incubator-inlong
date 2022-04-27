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

package org.apache.inlong.sort.protocol.node;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonInclude.Include;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonSubTypes;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.apache.inlong.sort.protocol.FieldInfo;
import org.apache.inlong.sort.protocol.node.extract.KafkaExtractNode;
import org.apache.inlong.sort.protocol.node.extract.MySqlExtractNode;
import org.apache.inlong.sort.protocol.node.load.HiveLoadNode;
import org.apache.inlong.sort.protocol.node.load.KafkaLoadNode;
import org.apache.inlong.sort.protocol.node.transform.DistinctNode;
import org.apache.inlong.sort.protocol.node.transform.TransformNode;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = MySqlExtractNode.class, name = "mysqlExtract"),
        @JsonSubTypes.Type(value = KafkaExtractNode.class, name = "kafkaExtract"),
        @JsonSubTypes.Type(value = TransformNode.class, name = "baseTransform"),
        @JsonSubTypes.Type(value = KafkaLoadNode.class, name = "kafkaLoad"),
        @JsonSubTypes.Type(value = DistinctNode.class, name = "distinct"),
        @JsonSubTypes.Type(value = HiveLoadNode.class, name = "hiveLoad")
})
public interface Node {

    String getId();

    @JsonInclude(Include.NON_NULL)
    String getName();

    List<FieldInfo> getFields();

    @JsonInclude(Include.NON_NULL)
    default Map<String, String> getProperties() {
        return new TreeMap<>();
    }

    @JsonInclude(Include.NON_NULL)
    default Map<String, String> tableOptions() {
        Map<String, String> options = new TreeMap<>();
        if (getProperties() != null && !getProperties().isEmpty()) {
            options.putAll(getProperties());
        }
        return options;
    }

    String genTableName();

    @JsonInclude(Include.NON_NULL)
    default String getPrimaryKey() {
        return null;
    }

    @JsonInclude(Include.NON_NULL)
    default List<FieldInfo> getPartitionFields() {
        return null;
    }

}
