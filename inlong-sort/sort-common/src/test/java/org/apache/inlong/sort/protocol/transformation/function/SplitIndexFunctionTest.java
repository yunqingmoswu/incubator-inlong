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

package org.apache.inlong.sort.protocol.transformation.function;

import org.apache.inlong.sort.formats.common.StringFormatInfo;
import org.apache.inlong.sort.protocol.FieldInfo;
import org.apache.inlong.sort.protocol.transformation.ConstantParam;
import org.apache.inlong.sort.protocol.transformation.Function;
import org.apache.inlong.sort.protocol.transformation.StringConstantParam;

/**
 * Test for {@link SplitIndexFunction}
 */
public class SplitIndexFunctionTest extends FunctionBaseTest {

    @Override
    public Function getFunction() {
        return new SplitIndexFunction(new FieldInfo("split_field", new StringFormatInfo()),
                new StringConstantParam(","),
                new ConstantParam(0));
    }

    @Override
    public String getExpectFormat() {
        return "SPLIT_INDEX(`split_field`, ',', 0)";
    }

    @Override
    public String getExpectSerializeStr() {
        return "{\"type\":\"splitIndex\",\"field\":{\"type\":\"base\",\"name\":\"split_field\","
                + "\"formatInfo\":{\"type\":\"string\"}},\"sep\":{\"type\":\"stringConstant\","
                + "\"value\":\",\"},\"index\":{\"type\":\"constant\",\"value\":0}}";

    }
}
