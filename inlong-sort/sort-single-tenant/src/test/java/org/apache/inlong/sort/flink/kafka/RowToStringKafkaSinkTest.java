/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.inlong.sort.flink.kafka;

import org.apache.flink.types.Row;
import org.apache.inlong.sort.formats.common.DoubleFormatInfo;
import org.apache.inlong.sort.formats.common.StringFormatInfo;
import org.apache.inlong.sort.protocol.FieldInfo;
import org.apache.inlong.sort.singletenant.flink.serialization.SerializationSchemaFactory;
import org.apache.kafka.common.utils.Bytes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class RowToStringKafkaSinkTest extends KafkaSinkTestBase {

    @Override
    protected void prepareData() throws IOException, ClassNotFoundException {
        topic = "test_kafka_row_to_string";
        serializationSchema = SerializationSchemaFactory.build(
                new FieldInfo[]{
                        new FieldInfo("f1", new StringFormatInfo()),
                        new FieldInfo("f2", new DoubleFormatInfo())
                },
                null
        );

        testRows = new ArrayList<>();
        testRows.add(Row.of("f1", 12.0));
        testRows.add(Row.of("f2", 12.1));
        testRows.add(Row.of("f3", 12.3));
    }

    @Override
    protected void verifyData(List<Bytes> results) {
        List<String> actualData = new ArrayList<>();
        results.forEach(value -> actualData.add(new String(value.get(), StandardCharsets.UTF_8)));
        actualData.sort(String::compareTo);

        List<String> expectedData = new ArrayList<>();
        testRows.forEach(row -> expectedData.add(row.toString()));
        expectedData.sort(String::compareTo);

        assertEquals(expectedData, actualData);
    }
}
