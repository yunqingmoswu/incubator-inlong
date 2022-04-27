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

import java.util.Arrays;
import java.util.HashMap;
import org.apache.inlong.sort.formats.common.LongFormatInfo;
import org.apache.inlong.sort.formats.common.StringFormatInfo;
import org.apache.inlong.sort.protocol.FieldInfo;
import org.apache.inlong.sort.protocol.node.load.HiveLoadNode;
import org.apache.inlong.sort.protocol.transformation.FieldRelationShip;

public class HiveLoadNodeTest extends NodeBaseTest {

    @Override
    public Node getNode() {
        return new HiveLoadNode("1", "test_hive_node",
                Arrays.asList(new FieldInfo("field", new StringFormatInfo())),
                Arrays.asList(new FieldRelationShip(new FieldInfo("field", new StringFormatInfo()),
                        new FieldInfo("field", new StringFormatInfo()))), null,
                1, new HashMap<>(), "myHive", "default", "test", "/opt/hive-conf", "3.1.2",
                null, Arrays.asList(new FieldInfo("day", new LongFormatInfo())));
    }

    @Override
    public String getExpectSerializeStr() {
        return "{\"type\":\"hiveLoad\",\"id\":\"1\","
                + "\"name\":\"test_hive_node\",\"fields\":"
                + "[{\"type\":\"base\",\"name\":\"field\","
                + "\"formatInfo\":{\"type\":\"string\"}}],"
                + "\"fieldRelationShips\":[{\"type\":"
                + "\"fieldRelationShip\",\"inputField\":"
                + "{\"type\":\"base\",\"name\":\"field\","
                + "\"formatInfo\":{\"type\":\"string\"}},"
                + "\"outputField\":{\"type\":\"base\",\"name\":"
                + "\"field\",\"formatInfo\":{\"type\":\"string\"}}}],"
                + "\"sinkParallelism\":1,\"properties\":{},\"catalogName\":"
                + "\"myHive\",\"database\":\"default\",\"tableName\":\"test\","
                + "\"hiveConfDir\":\"/opt/hive-conf\",\"hiveVersion\":\"3.1.2\","
                + "\"hadoopConfDir\":null,\"partitionFields\":[{\"type\":\"base\","
                + "\"name\":\"day\",\"formatInfo\":{\"type\":\"long\"}}]}";
    }
}