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

package org.apache.inlong.manager.client.api.util;

import com.google.common.collect.Lists;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.inlong.manager.client.api.DataSeparator;
import org.apache.inlong.manager.client.api.auth.DefaultAuthentication;
import org.apache.inlong.manager.client.api.sink.ClickHouseSink;
import org.apache.inlong.manager.client.api.sink.HiveSink;
import org.apache.inlong.manager.client.api.sink.KafkaSink;
import org.apache.inlong.manager.common.enums.DataFormat;
import org.apache.inlong.manager.common.enums.FieldType;
import org.apache.inlong.manager.common.enums.FileFormat;
import org.apache.inlong.manager.common.enums.GlobalConstants;
import org.apache.inlong.manager.common.enums.SinkType;
import org.apache.inlong.manager.common.pojo.sink.SinkFieldRequest;
import org.apache.inlong.manager.common.pojo.sink.SinkFieldResponse;
import org.apache.inlong.manager.common.pojo.sink.SinkRequest;
import org.apache.inlong.manager.common.pojo.sink.SinkResponse;
import org.apache.inlong.manager.common.pojo.sink.ck.ClickHouseSinkRequest;
import org.apache.inlong.manager.common.pojo.sink.ck.ClickHouseSinkResponse;
import org.apache.inlong.manager.common.pojo.sink.hive.HiveSinkRequest;
import org.apache.inlong.manager.common.pojo.sink.hive.HiveSinkResponse;
import org.apache.inlong.manager.common.pojo.sink.kafka.KafkaSinkRequest;
import org.apache.inlong.manager.common.pojo.sink.kafka.KafkaSinkResponse;
import org.apache.inlong.manager.common.pojo.stream.InlongStreamInfo;
import org.apache.inlong.manager.common.pojo.stream.SinkField;
import org.apache.inlong.manager.common.pojo.stream.StreamSink;
import org.apache.inlong.manager.common.util.CommonBeanUtils;

import java.nio.charset.Charset;
import java.util.List;
import java.util.stream.Collectors;

public class InlongStreamSinkTransfer {

    public static SinkRequest createSinkRequest(StreamSink streamSink, InlongStreamInfo streamInfo) {
        SinkType sinkType = streamSink.getSinkType();
        SinkRequest sinkRequest;
        if (sinkType == SinkType.HIVE) {
            sinkRequest = createHiveRequest(streamSink, streamInfo);
        } else if (sinkType == SinkType.KAFKA) {
            sinkRequest = createKafkaRequest(streamSink, streamInfo);
        } else if (sinkType == SinkType.CLICKHOUSE) {
            sinkRequest = createClickHouseRequest(streamSink, streamInfo);
        } else {
            throw new IllegalArgumentException(String.format("Unsupported sink type : %s for Inlong", sinkType));
        }
        return sinkRequest;
    }

    public static StreamSink parseStreamSink(SinkResponse sinkResponse) {
        return parseStreamSink(sinkResponse, null);
    }

    public static StreamSink parseStreamSink(SinkResponse sinkResponse, StreamSink streamSink) {
        String type = sinkResponse.getSinkType();
        SinkType sinkType = SinkType.forType(type);
        StreamSink streamSinkResult;
        if (sinkType == SinkType.HIVE) {
            streamSinkResult = parseHiveSink((HiveSinkResponse) sinkResponse, streamSink);
        } else if (sinkType == SinkType.KAFKA) {
            streamSinkResult = parseKafkaSink((KafkaSinkResponse) sinkResponse, streamSink);
        } else if (sinkType == SinkType.CLICKHOUSE) {
            streamSinkResult = parseClickHouseSink((ClickHouseSinkResponse) sinkResponse, streamSink);
        } else {
            throw new IllegalArgumentException(String.format("Unsupported sink type : %s for Inlong", sinkType));
        }
        return streamSinkResult;
    }

    private static SinkRequest createClickHouseRequest(StreamSink streamSink, InlongStreamInfo streamInfo) {
        ClickHouseSinkRequest clickHouseSinkRequest = new ClickHouseSinkRequest();
        ClickHouseSink clickHouseSink = (ClickHouseSink) streamSink;
        clickHouseSinkRequest.setSinkName(clickHouseSink.getSinkName());
        clickHouseSinkRequest.setDbName(clickHouseSink.getDbName());
        clickHouseSinkRequest.setSinkType(clickHouseSink.getSinkType().name());
        clickHouseSinkRequest.setJdbcUrl(clickHouseSink.getJdbcUrl());
        DefaultAuthentication defaultAuthentication = clickHouseSink.getAuthentication();
        AssertUtil.notNull(defaultAuthentication,
                String.format("Clickhouse storage:%s must be authenticated", clickHouseSink.getDbName()));
        clickHouseSinkRequest.setUsername(defaultAuthentication.getUserName());
        clickHouseSinkRequest.setPassword(defaultAuthentication.getPassword());
        clickHouseSinkRequest.setTableName(clickHouseSink.getTableName());
        clickHouseSinkRequest.setIsDistributed(clickHouseSink.getIsDistributed());
        clickHouseSinkRequest.setFlushInterval(clickHouseSink.getFlushInterval());
        clickHouseSinkRequest.setFlushRecord(clickHouseSink.getFlushRecord());
        clickHouseSinkRequest.setKeyFieldNames(clickHouseSink.getKeyFieldNames());
        clickHouseSinkRequest.setPartitionFields(clickHouseSink.getPartitionFields());
        clickHouseSinkRequest.setPartitionStrategy(clickHouseSink.getPartitionStrategy());
        clickHouseSinkRequest.setRetryTimes(clickHouseSink.getRetryTimes());
        clickHouseSinkRequest.setInlongGroupId(streamInfo.getInlongGroupId());
        clickHouseSinkRequest.setInlongStreamId(streamInfo.getInlongStreamId());
        clickHouseSinkRequest.setProperties(clickHouseSink.getProperties());
        clickHouseSinkRequest.setEnableCreateResource(clickHouseSink.isNeedCreated() ? 1 : 0);
        if (CollectionUtils.isNotEmpty(clickHouseSink.getSinkFields())) {
            List<SinkFieldRequest> fieldRequests = createSinkFieldRequests(streamSink.getSinkFields());
            clickHouseSinkRequest.setFieldList(fieldRequests);
        }
        return clickHouseSinkRequest;
    }

    private static StreamSink parseClickHouseSink(ClickHouseSinkResponse sinkResponse,
            StreamSink streamSink) {
        ClickHouseSink clickHouseSink = new ClickHouseSink();
        if (streamSink != null) {
            AssertUtil.isTrue(sinkResponse.getSinkName().equals(streamSink.getSinkName()),
                    String.format("SinkName is not equal: %s != %s", sinkResponse, streamSink));
            ClickHouseSink snapshot = (ClickHouseSink) streamSink;
            clickHouseSink = CommonBeanUtils.copyProperties(snapshot, ClickHouseSink::new);
        } else {
            clickHouseSink.setIsDistributed(sinkResponse.getIsDistributed());
            clickHouseSink.setSinkName(sinkResponse.getSinkName());
            clickHouseSink.setFlushInterval(sinkResponse.getFlushInterval());
            clickHouseSink.setAuthentication(new DefaultAuthentication(sinkResponse.getSinkName(),
                    sinkResponse.getPassword()));
            clickHouseSink.setDbName(sinkResponse.getDbName());
            clickHouseSink.setFlushRecord(sinkResponse.getFlushRecord());
            clickHouseSink.setJdbcUrl(sinkResponse.getJdbcUrl());
            clickHouseSink.setPartitionFields(sinkResponse.getPartitionFields());
            clickHouseSink.setKeyFieldNames(sinkResponse.getKeyFieldNames());
            clickHouseSink.setPartitionStrategy(sinkResponse.getPartitionStrategy());
            clickHouseSink.setRetryTimes(sinkResponse.getRetryTimes());
            clickHouseSink.setIsDistributed(sinkResponse.getIsDistributed());
        }
        clickHouseSink.setProperties(sinkResponse.getProperties());
        clickHouseSink.setNeedCreated(
                GlobalConstants.ENABLE_CREATE_RESOURCE.equals(sinkResponse.getEnableCreateResource()));
        if (CollectionUtils.isNotEmpty(sinkResponse.getFieldList())) {
            clickHouseSink.setSinkFields(convertToSinkFields(sinkResponse.getFieldList()));
        }
        return clickHouseSink;
    }

    private static List<SinkField> convertToSinkFields(List<SinkFieldResponse> sinkFieldResponses) {
        return sinkFieldResponses.stream().map(sinkFieldResponse -> new SinkField(sinkFieldResponse.getId(),
                FieldType.forName(sinkFieldResponse.getFieldType()),
                sinkFieldResponse.getFieldName(),
                sinkFieldResponse.getFieldComment(),
                null, sinkFieldResponse.getSourceFieldName(),
                StringUtils.isBlank(sinkFieldResponse.getSourceFieldType()) ? null :
                        FieldType.forName(sinkFieldResponse.getSourceFieldType()),
                sinkFieldResponse.getIsMetaField(),
                sinkFieldResponse.getFieldFormat())).collect(Collectors.toList());

    }

    private static SinkRequest createKafkaRequest(StreamSink streamSink, InlongStreamInfo streamInfo) {
        KafkaSinkRequest kafkaSinkRequest = new KafkaSinkRequest();
        KafkaSink kafkaSink = (KafkaSink) streamSink;
        kafkaSinkRequest.setSinkName(streamSink.getSinkName());
        kafkaSinkRequest.setBootstrapServers(kafkaSink.getBootstrapServers());
        kafkaSinkRequest.setTopicName(kafkaSink.getTopicName());
        kafkaSinkRequest.setSinkType(kafkaSink.getSinkType().name());
        kafkaSinkRequest.setInlongGroupId(streamInfo.getInlongGroupId());
        kafkaSinkRequest.setInlongStreamId(streamInfo.getInlongStreamId());
        kafkaSinkRequest.setSerializationType(kafkaSink.getDataFormat().name());
        kafkaSinkRequest.setEnableCreateResource(kafkaSink.isNeedCreated() ? 1 : 0);
        kafkaSinkRequest.setProperties(kafkaSink.getProperties());
        kafkaSinkRequest.setPrimaryKey(kafkaSink.getPrimaryKey());
        if (CollectionUtils.isNotEmpty(kafkaSink.getSinkFields())) {
            List<SinkFieldRequest> fieldRequests = createSinkFieldRequests(kafkaSink.getSinkFields());
            kafkaSinkRequest.setFieldList(fieldRequests);
        }
        return kafkaSinkRequest;
    }

    private static StreamSink parseKafkaSink(KafkaSinkResponse sinkResponse, StreamSink sink) {
        KafkaSink kafkaSink = new KafkaSink();
        if (sink != null) {
            AssertUtil.isTrue(sinkResponse.getSinkName().equals(sink.getSinkName()),
                    String.format("SinkName is not equal: %s != %s", sinkResponse, sink));
            KafkaSink snapshot = (KafkaSink) sink;
            kafkaSink.setSinkName(snapshot.getSinkName());
            kafkaSink.setBootstrapServers(snapshot.getBootstrapServers());
            kafkaSink.setTopicName(snapshot.getTopicName());
            kafkaSink.setDataFormat(snapshot.getDataFormat());
        } else {
            kafkaSink.setSinkName(sinkResponse.getSinkName());
            kafkaSink.setBootstrapServers(sinkResponse.getBootstrapServers());
            kafkaSink.setTopicName(sinkResponse.getTopicName());
            kafkaSink.setDataFormat(DataFormat.forName(sinkResponse.getSerializationType()));
        }
        kafkaSink.setPrimaryKey(sinkResponse.getPrimaryKey());
        kafkaSink.setProperties(sinkResponse.getProperties());
        kafkaSink.setNeedCreated(GlobalConstants.ENABLE_CREATE_RESOURCE.equals(sinkResponse.getEnableCreateResource()));
        if (CollectionUtils.isNotEmpty(sinkResponse.getFieldList())) {
            kafkaSink.setSinkFields(convertToSinkFields(sinkResponse.getFieldList()));
        }
        return kafkaSink;
    }

    private static HiveSinkRequest createHiveRequest(StreamSink streamSink, InlongStreamInfo streamInfo) {
        HiveSinkRequest hiveSinkRequest = new HiveSinkRequest();
        HiveSink hiveSink = (HiveSink) streamSink;
        hiveSinkRequest.setSinkName(streamSink.getSinkName());
        hiveSinkRequest.setInlongGroupId(streamInfo.getInlongGroupId());
        hiveSinkRequest.setInlongStreamId(streamInfo.getInlongStreamId());
        hiveSinkRequest.setDataEncoding(hiveSink.getCharset().name());
        hiveSinkRequest.setEnableCreateTable(hiveSink.isNeedCreated() ? 1 : 0);
        hiveSinkRequest.setDataSeparator(String.valueOf(hiveSink.getDataSeparator().getAsciiCode()));
        hiveSinkRequest.setDbName(hiveSink.getDbName());
        hiveSinkRequest.setTableName(hiveSink.getTableName());
        hiveSinkRequest.setDataPath(hiveSink.getDataPath());
        hiveSinkRequest.setJdbcUrl(hiveSink.getJdbcUrl());
        hiveSinkRequest.setFileFormat(hiveSink.getFileFormat().name());
        hiveSinkRequest.setSinkType(hiveSink.getSinkType().name());
        hiveSinkRequest.setPartitionFieldList(hiveSink.getPartitionFieldList());
        hiveSinkRequest.setHiveConfDir(hiveSink.getHiveConfDir());
        hiveSinkRequest.setHiveVersion(hiveSink.getHiveVersion());
        DefaultAuthentication defaultAuthentication = hiveSink.getAuthentication();
        AssertUtil.notNull(defaultAuthentication,
                String.format("Hive storage:%s must be authenticated", hiveSink.getDbName()));
        hiveSinkRequest.setUsername(defaultAuthentication.getUserName());
        hiveSinkRequest.setPassword(defaultAuthentication.getPassword());
        hiveSinkRequest.setProperties(hiveSink.getProperties());
        if (CollectionUtils.isNotEmpty(hiveSink.getSinkFields())) {
            List<SinkFieldRequest> fieldRequests = createSinkFieldRequests(streamSink.getSinkFields());
            hiveSinkRequest.setFieldList(fieldRequests);
        }
        return hiveSinkRequest;
    }

    private static List<SinkFieldRequest> createSinkFieldRequests(List<SinkField> sinkFields) {
        List<SinkFieldRequest> fieldRequestList = Lists.newArrayList();
        for (SinkField sinkField : sinkFields) {
            SinkFieldRequest request = new SinkFieldRequest();
            request.setFieldName(sinkField.getFieldName());
            request.setFieldType(sinkField.getFieldType().toString());
            request.setFieldComment(sinkField.getFieldComment());
            request.setSourceFieldName(sinkField.getSourceFieldName());
            request.setSourceFieldType(
                    sinkField.getSourceFieldType() == null ? null : sinkField.getSourceFieldType().toString());
            request.setIsMetaField(sinkField.getIsMetaField());
            request.setFieldFormat(sinkField.getFieldFormat());
            fieldRequestList.add(request);
        }
        return fieldRequestList;
    }

    private static HiveSink parseHiveSink(HiveSinkResponse sinkResponse, StreamSink sink) {
        HiveSink hiveSink = new HiveSink();
        if (sink != null) {
            AssertUtil.isTrue(sinkResponse.getSinkName().equals(sink.getSinkName()),
                    String.format("SinkName is not equal: %s != %s", sinkResponse, sink));
            HiveSink snapshot = (HiveSink) sink;
            hiveSink.setSinkName(snapshot.getSinkName());
            hiveSink.setDataSeparator(snapshot.getDataSeparator());
            hiveSink.setCharset(snapshot.getCharset());
            hiveSink.setAuthentication(snapshot.getAuthentication());
            hiveSink.setFileFormat(snapshot.getFileFormat());
            hiveSink.setJdbcUrl(snapshot.getJdbcUrl());
            hiveSink.setTableName(snapshot.getTableName());
            hiveSink.setDbName(snapshot.getDbName());
            hiveSink.setDataPath(snapshot.getDataPath());
            hiveSink.setHiveVersion(snapshot.getHiveVersion());
            hiveSink.setHiveConfDir(snapshot.getHiveConfDir());
            hiveSink.setPartitionFieldList(snapshot.getPartitionFieldList());
        } else {
            hiveSink.setSinkName(sinkResponse.getSinkName());
            hiveSink.setDataSeparator(DataSeparator.forAscii(Integer.parseInt(sinkResponse.getDataSeparator())));
            hiveSink.setCharset(Charset.forName(sinkResponse.getDataEncoding()));
            String password = sinkResponse.getPassword();
            String uname = sinkResponse.getUsername();
            hiveSink.setAuthentication(new DefaultAuthentication(uname, password));
            hiveSink.setFileFormat(FileFormat.forName(sinkResponse.getFileFormat()));
            hiveSink.setJdbcUrl(sinkResponse.getJdbcUrl());
            hiveSink.setTableName(sinkResponse.getTableName());
            hiveSink.setDbName(sinkResponse.getDbName());
            hiveSink.setDataPath(sinkResponse.getDataPath());
            hiveSink.setHiveConfDir(sinkResponse.getHiveConfDir());
            hiveSink.setHiveVersion(sinkResponse.getHiveVersion());
            hiveSink.setPartitionFieldList(sinkResponse.getPartitionFieldList());
        }

        hiveSink.setProperties(sinkResponse.getProperties());
        hiveSink.setSinkType(SinkType.HIVE);
        hiveSink.setNeedCreated(GlobalConstants.ENABLE_CREATE_RESOURCE.equals(sinkResponse.getEnableCreateResource()));
        if (CollectionUtils.isNotEmpty(sinkResponse.getFieldList())) {
            hiveSink.setSinkFields(convertToSinkFields(sinkResponse.getFieldList()));
        }
        return hiveSink;
    }

}
