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

package org.apache.inlong.sort.kafka;

import org.apache.commons.lang3.StringUtils;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.JsonNode;
import org.apache.flink.streaming.connectors.kafka.KafkaContextAware;
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema;
import org.apache.flink.streaming.connectors.kafka.partitioner.FlinkKafkaPartitioner;
import org.apache.flink.table.data.GenericRowData;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.formats.raw.RawFormatSerializationSchema;
import org.apache.flink.types.RowKind;
import org.apache.flink.util.Preconditions;
import org.apache.inlong.sort.base.format.DynamicSchemaFormatFactory;
import org.apache.inlong.sort.base.format.JsonDynamicSchemaFormat;
import org.apache.inlong.sort.kafka.KafkaDynamicSink.WritableMetadata;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * A specific {@link KafkaSerializationSchema} for {@link KafkaDynamicSink}.
 */
class DynamicKafkaSerializationSchema implements KafkaSerializationSchema<RowData>, KafkaContextAware<RowData> {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(DynamicKafkaSerializationSchema.class);

    private final @Nullable
    FlinkKafkaPartitioner<RowData> partitioner;

    private final String topic;

    private final @Nullable
    SerializationSchema<RowData> keySerialization;

    private final SerializationSchema<RowData> valueSerialization;

    private final RowData.FieldGetter[] keyFieldGetters;

    private final RowData.FieldGetter[] valueFieldGetters;

    private final boolean hasMetadata;

    private final boolean upsertMode;

    private final String topicPattern;
    /**
     * Contains the position for each value of {@link WritableMetadata} in the
     * consumed row or -1 if this metadata key is not used.
     */
    private final int[] metadataPositions;
    private final String sinkMultipleFormat;
    private boolean multipleSink;
    private JsonDynamicSchemaFormat jsonDynamicSchemaFormat;
    private int[] partitions;

    private int parallelInstanceId;

    private int numParallelInstances;

    DynamicKafkaSerializationSchema(
            String topic,
            @Nullable FlinkKafkaPartitioner<RowData> partitioner,
            @Nullable SerializationSchema<RowData> keySerialization,
            SerializationSchema<RowData> valueSerialization,
            RowData.FieldGetter[] keyFieldGetters,
            RowData.FieldGetter[] valueFieldGetters,
            boolean hasMetadata,
            int[] metadataPositions,
            boolean upsertMode,
            @Nullable String sinkMultipleFormat,
            @Nullable String topicPattern) {
        if (upsertMode) {
            Preconditions.checkArgument(
                    keySerialization != null && keyFieldGetters.length > 0,
                    "Key must be set in upsert mode for serialization schema.");
        }
        this.topic = topic;
        this.partitioner = partitioner;
        this.keySerialization = keySerialization;
        this.valueSerialization = valueSerialization;
        this.keyFieldGetters = keyFieldGetters;
        this.valueFieldGetters = valueFieldGetters;
        this.hasMetadata = hasMetadata;
        this.metadataPositions = metadataPositions;
        this.upsertMode = upsertMode;
        this.sinkMultipleFormat = sinkMultipleFormat;
        this.topicPattern = topicPattern;
    }

    static RowData createProjectedRow(
            RowData consumedRow, RowKind kind, RowData.FieldGetter[] fieldGetters) {
        final int arity = fieldGetters.length;
        final GenericRowData genericRowData = new GenericRowData(kind, arity);
        for (int fieldPos = 0; fieldPos < arity; fieldPos++) {
            genericRowData.setField(fieldPos, fieldGetters[fieldPos].getFieldOrNull(consumedRow));
        }
        return genericRowData;
    }

    @Override
    public void open(SerializationSchema.InitializationContext context) throws Exception {
        if (keySerialization != null) {
            keySerialization.open(context);
        }
        valueSerialization.open(context);
        if (partitioner != null) {
            partitioner.open(parallelInstanceId, numParallelInstances);
        }
        // Only support dynamic topic when the topicPattern is specified
        //      and the valueSerialization is RawFormatSerializationSchema
        if (valueSerialization instanceof RawFormatSerializationSchema && StringUtils.isNotBlank(topicPattern)) {
            multipleSink = true;
            jsonDynamicSchemaFormat =
                    (JsonDynamicSchemaFormat) DynamicSchemaFormatFactory.getFormat(sinkMultipleFormat);
        }
    }

    @Override
    public ProducerRecord<byte[], byte[]> serialize(RowData consumedRow, @Nullable Long timestamp) {
        // shortcut in case no input projection is required
        if (keySerialization == null && !hasMetadata) {
            final byte[] valueSerialized = valueSerialization.serialize(consumedRow);
            return new ProducerRecord<>(
                    getTargetTopic(consumedRow),
                    extractPartition(consumedRow, null, valueSerialized),
                    null,
                    valueSerialized);
        }
        final byte[] keySerialized;
        if (keySerialization == null) {
            keySerialized = null;
        } else {
            final RowData keyRow = createProjectedRow(consumedRow, RowKind.INSERT, keyFieldGetters);
            keySerialized = keySerialization.serialize(keyRow);
        }

        final byte[] valueSerialized;
        final RowKind kind = consumedRow.getRowKind();
        final RowData valueRow = createProjectedRow(consumedRow, kind, valueFieldGetters);
        if (upsertMode) {
            if (kind == RowKind.DELETE || kind == RowKind.UPDATE_BEFORE) {
                // transform the message as the tombstone message
                valueSerialized = null;
            } else {
                // make the message to be INSERT to be compliant with the INSERT-ONLY format
                valueRow.setRowKind(RowKind.INSERT);
                valueSerialized = valueSerialization.serialize(valueRow);
            }
        } else {
            valueSerialized = valueSerialization.serialize(valueRow);
        }
        return new ProducerRecord<>(
                getTargetTopic(consumedRow),
                extractPartition(consumedRow, keySerialized, valueSerialized),
                readMetadata(consumedRow, KafkaDynamicSink.WritableMetadata.TIMESTAMP),
                keySerialized,
                valueSerialized,
                readMetadata(consumedRow, KafkaDynamicSink.WritableMetadata.HEADERS));
    }

    /**
     * Serialize for list it is used for multiple sink scenes when a record contains mulitple real records.
     *
     * @param consumedRow The consumeRow
     * @param timestamp The timestamp
     * @return List of ProducerRecord
     */
    public List<ProducerRecord<byte[], byte[]>> serializeForList(RowData consumedRow, @Nullable Long timestamp) {
        if (!multipleSink) {
            return Collections.singletonList(serialize(consumedRow, timestamp));
        }
        List<ProducerRecord<byte[], byte[]>> values = new ArrayList<>();
        try {
            JsonNode rootNode = jsonDynamicSchemaFormat.deserialize(consumedRow.getBinary(0));
            boolean isDDL = jsonDynamicSchemaFormat.extractDDLFlag(rootNode);
            if (isDDL) {
                values.add(new ProducerRecord<>(
                        jsonDynamicSchemaFormat.parse(rootNode, topicPattern),
                        extractPartition(consumedRow, null, consumedRow.getBinary(0)),
                        null,
                        consumedRow.getBinary(0)));
                return values;
            }
            JsonNode updateBeforeNode = jsonDynamicSchemaFormat.getUpdateBefore(rootNode);
            JsonNode updateAfterNode = jsonDynamicSchemaFormat.getUpdateAfter(rootNode);
            boolean splitRequired = (updateAfterNode != null && updateAfterNode.isArray()
                    && updateAfterNode.size() > 1) || (updateBeforeNode != null && updateBeforeNode.isArray()
                    && updateBeforeNode.size() > 1);
            if (!splitRequired) {
                values.add(new ProducerRecord<>(
                        jsonDynamicSchemaFormat.parse(rootNode, topicPattern),
                        extractPartition(consumedRow, null, consumedRow.getBinary(0)),
                        null, consumedRow.getBinary(0)));
            } else {
                split2JsonArray(rootNode, updateBeforeNode, updateAfterNode, values);
            }
        } catch (IOException e) {
            LOG.warn("deserialize error", e);
            values.add(new ProducerRecord<>(topic, null, null, consumedRow.getBinary(0)));
        }
        return values;
    }

    private void split2JsonArray(JsonNode rootNode,
            JsonNode updateBeforeNode, JsonNode updateAfterNode, List<ProducerRecord<byte[], byte[]>> values) {
        Iterator<Entry<String, JsonNode>> iterator = rootNode.fields();
        Map<String, Object> baseMap = new LinkedHashMap<>();
        String updateBeforeKey = null;
        String updateAfterKey = null;
        while (iterator.hasNext()) {
            Entry<String, JsonNode> kv = iterator.next();
            if (kv.getValue() == null || (!kv.getValue().equals(updateBeforeNode) && !kv.getValue()
                    .equals(updateAfterNode))) {
                baseMap.put(kv.getKey(), kv.getValue());
                continue;
            }
            if (kv.getValue().equals(updateAfterNode)) {
                updateAfterKey = kv.getKey();
            } else if (kv.getValue().equals(updateBeforeNode)) {
                updateBeforeKey = kv.getKey();
            }
        }
        if (updateAfterNode != null) {
            for (int i = 0; i < updateAfterNode.size(); i++) {
                baseMap.put(updateAfterKey, Collections.singletonList(updateAfterNode.get(i)));
                if (updateBeforeNode != null && updateBeforeNode.size() > i) {
                    baseMap.put(updateBeforeKey, Collections.singletonList(updateBeforeNode.get(i)));
                } else if (updateBeforeKey != null) {
                    baseMap.remove(updateBeforeKey);
                }
                try {
                    byte[] data = jsonDynamicSchemaFormat.objectMapper.writeValueAsBytes(baseMap);
                    values.add(new ProducerRecord<>(
                            jsonDynamicSchemaFormat.parse(rootNode, topicPattern),
                            extractPartition(null, null, data), null, data));
                } catch (Exception e) {
                    throw new RuntimeException("serialize for list error", e);
                }
            }
        } else {
            // In general, it will not run to this branch
            for (int i = 0; i < updateBeforeNode.size(); i++) {
                baseMap.put(updateBeforeKey, Collections.singletonList(updateBeforeNode.get(i)));
                try {
                    byte[] data = jsonDynamicSchemaFormat.objectMapper.writeValueAsBytes(baseMap);
                    values.add(new ProducerRecord<>(
                            jsonDynamicSchemaFormat.parse(rootNode, topicPattern),
                            extractPartition(null, null, data), null, data));
                } catch (Exception e) {
                    throw new RuntimeException("serialize for list error", e);
                }
            }
        }
    }

    @Override
    public void setParallelInstanceId(int parallelInstanceId) {
        this.parallelInstanceId = parallelInstanceId;
    }

    @Override
    public void setNumParallelInstances(int numParallelInstances) {
        this.numParallelInstances = numParallelInstances;
    }

    @Override
    public void setPartitions(int[] partitions) {
        this.partitions = partitions;
    }

    @Override
    public String getTargetTopic(RowData element) {
        if (multipleSink) {
            try {
                //  Extract the index '0' as the raw data is determined by the Raw format:
                //  The Raw format allows to read and write raw (byte based) values as a single column
                return jsonDynamicSchemaFormat.parse(element.getBinary(0), topicPattern);
            } catch (IOException e) {
                // Ignore the parse error and it will return the default topic final.
                LOG.warn("parse dynamic topic error", e);
            }
        }
        return topic;
    }

    @SuppressWarnings("unchecked")
    private <T> T readMetadata(RowData consumedRow, KafkaDynamicSink.WritableMetadata metadata) {
        final int pos = metadataPositions[metadata.ordinal()];
        if (pos < 0) {
            return null;
        }
        return (T) metadata.converter.read(consumedRow, pos);
    }

    private Integer extractPartition(
            RowData consumedRow, @Nullable byte[] keySerialized, byte[] valueSerialized) {
        if (partitioner != null) {
            return partitioner.partition(
                    consumedRow, keySerialized, valueSerialized, topic, partitions);
        }
        return null;
    }

    // --------------------------------------------------------------------------------------------

    interface MetadataConverter extends Serializable {

        Object read(RowData consumedRow, int pos);
    }
}
