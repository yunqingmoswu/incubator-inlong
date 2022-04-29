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

package org.apache.inlong.sort.singletenant.flink.parser.impl;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.inlong.sort.formats.base.TableFormatUtils;
import org.apache.inlong.sort.protocol.BuiltInFieldInfo;
import org.apache.inlong.sort.protocol.BuiltInFieldInfo.BuiltInField;
import org.apache.inlong.sort.protocol.FieldInfo;
import org.apache.inlong.sort.protocol.GroupInfo;
import org.apache.inlong.sort.protocol.StreamInfo;
import org.apache.inlong.sort.protocol.node.ExtractNode;
import org.apache.inlong.sort.protocol.node.LoadNode;
import org.apache.inlong.sort.protocol.node.Node;
import org.apache.inlong.sort.protocol.node.extract.KafkaExtractNode;
import org.apache.inlong.sort.protocol.node.extract.MySqlExtractNode;
import org.apache.inlong.sort.protocol.node.load.KafkaLoadNode;
import org.apache.inlong.sort.protocol.node.transform.DistinctNode;
import org.apache.inlong.sort.protocol.node.transform.TransformNode;
import org.apache.inlong.sort.protocol.transformation.FieldRelationShip;
import org.apache.inlong.sort.protocol.transformation.FilterFunction;
import org.apache.inlong.sort.protocol.transformation.Function;
import org.apache.inlong.sort.protocol.transformation.FunctionParam;
import org.apache.inlong.sort.protocol.transformation.relation.JoinRelationShip;
import org.apache.inlong.sort.protocol.transformation.relation.NodeRelationShip;
import org.apache.inlong.sort.protocol.transformation.relation.UnionNodeRelationShip;
import org.apache.inlong.sort.singletenant.flink.parser.Parser;
import org.apache.inlong.sort.singletenant.flink.parser.result.FlinkSqlParseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Flink sql parse handler
 * It accepts a Tableenv and GroupInfo, and outputs the parsed FlinkSqlParseResult
 */
public class FlinkSqlParser implements Parser {

    private static final Logger log = LoggerFactory.getLogger(FlinkSqlParser.class);

    private final TableEnvironment tableEnv;
    private final GroupInfo groupInfo;
    private final Set<String> hasParsedSet = new HashSet<>();
    private final Map<String, String> extractTableSqls = new TreeMap<>();
    private final Map<String, String> transformTableSqls = new TreeMap<>();
    private final Map<String, String> loadTableSqls = new TreeMap<>();
    private final List<String> insertSqls = new ArrayList<>();

    /**
     * Flink sql parse constructor
     *
     * @param tableEnv The tableEnv,it is the execution environment of flink sql
     * @param groupInfo The groupInfo,it is the data model abstraction of task execution
     */
    public FlinkSqlParser(TableEnvironment tableEnv, GroupInfo groupInfo) {
        this.tableEnv = tableEnv;
        this.groupInfo = groupInfo;
    }

    /**
     * Get a instance of FlinkSqlParser
     *
     * @param tableEnv The tableEnv,it is the execution environment of flink sql
     * @param groupInfo The groupInfo,it is the data model abstraction of task execution
     * @return FlinkSqlParser The flink sql parse handler
     */
    public static FlinkSqlParser getInstance(TableEnvironment tableEnv, GroupInfo groupInfo) {
        return new FlinkSqlParser(tableEnv, groupInfo);
    }

    /**
     * Sql parse entrance
     *
     * @return FlinkSqlParseResult the result of sql parsed
     */
    @Override
    public FlinkSqlParseResult parse() {
        Preconditions.checkNotNull(groupInfo, "group info is null");
        Preconditions.checkNotNull(groupInfo.getStreams(), "streams is null");
        Preconditions.checkState(!groupInfo.getStreams().isEmpty(), "streams is empty");
        Preconditions.checkNotNull(tableEnv, "tableEnv is null");
        log.info("start parse group, groupId:{}", groupInfo.getGroupId());
        for (StreamInfo streamInfo : groupInfo.getStreams()) {
            parseStream(streamInfo);
        }
        log.info("parse group success, groupId:{}", groupInfo.getGroupId());
        List<String> createTableSqls = new ArrayList<>(extractTableSqls.values());
        createTableSqls.addAll(transformTableSqls.values());
        createTableSqls.addAll(loadTableSqls.values());
        return new FlinkSqlParseResult(tableEnv, createTableSqls, insertSqls);
    }

    /**
     * Parse stream
     *
     * @param streamInfo The encapsulation of nodes and node relationships
     */
    private void parseStream(StreamInfo streamInfo) {
        Preconditions.checkNotNull(streamInfo, "stream is null");
        Preconditions.checkNotNull(streamInfo.getStreamId(), "streamId is null");
        Preconditions.checkNotNull(streamInfo.getNodes(), "nodes is null");
        Preconditions.checkState(!streamInfo.getNodes().isEmpty(), "nodes is empty");
        Preconditions.checkNotNull(streamInfo.getRelations(), "relations is null");
        Preconditions.checkState(!streamInfo.getRelations().isEmpty(), "relations is empty");
        log.info("start parse stream, streamId:{}", streamInfo.getStreamId());
        Map<String, Node> nodeMap = new HashMap<>(streamInfo.getNodes().size());
        streamInfo.getNodes().forEach(s -> {
            Preconditions.checkNotNull(s.getId(), "node id is null");
            nodeMap.put(s.getId(), s);
        });
        Map<String, NodeRelationShip> relationMap = new HashMap<String, NodeRelationShip>();
        streamInfo.getRelations().forEach(r -> {
            for (String output : r.getOutputs()) {
                relationMap.put(output, r);
            }
        });
        streamInfo.getRelations().forEach(r -> {
            parseNodeRelation(r, nodeMap, relationMap);
        });
        log.info("parse stream success, streamId:{}", streamInfo.getStreamId());
    }

    /**
     * parse node relation
     *
     * Here we only parse the output node in the relationship,
     * and the input node parsing is achieved by parsing the dependent node parsing of the output node.
     *
     * @param relation Define relationships between nodes, it also shows the data flow
     * @param nodeMap Store the mapping relationship between node id and node
     * @param relationMap Store the mapping relationship between node id and relation
     */
    private void parseNodeRelation(NodeRelationShip relation, Map<String, Node> nodeMap,
            Map<String, NodeRelationShip> relationMap) {
        log.info("start parse node relation, relation:{}", relation);
        Preconditions.checkNotNull(relation, "relation is null");
        Preconditions.checkState(relation.getInputs().size() > 0,
                "relation must have at least one input node");
        Preconditions.checkState(relation.getOutputs().size() > 0,
                "relation must have at least one output node");
        relation.getOutputs().forEach(s -> {
            Preconditions.checkNotNull(s, "node id in outputs is null");
            Node node = nodeMap.get(s);
            Preconditions.checkNotNull(node, "can not find any node by node id " + s);
            parseNode(node, relation, nodeMap, relationMap);
        });
        log.info("parse node relation success, relation:{}", relation);
    }

    private void registerTableSql(Node node, String sql) {
        if (node instanceof ExtractNode) {
            extractTableSqls.put(node.getId(), sql);
        } else if (node instanceof TransformNode) {
            transformTableSqls.put(node.getId(), sql);
        } else if (node instanceof LoadNode) {
            loadTableSqls.put(node.getId(), sql);
        } else {
            throw new UnsupportedOperationException("Only support [ExtractNode|TransformNode|LoadNode]");
        }
    }

    /**
     * Parse a node and recursively resolve its dependent nodes
     *
     * @param node The abstract of extract, transform, load
     * @param relation Define relationships between nodes, it also shows the data flow
     * @param nodeMap store the mapping relationship between node id and node
     * @param relationMap Store the mapping relationship between node id and relation
     */
    private void parseNode(Node node, NodeRelationShip relation, Map<String, Node> nodeMap,
            Map<String, NodeRelationShip> relationMap) {
        if (hasParsedSet.contains(node.getId())) {
            log.warn("the node has already been parsed, node id:{}", node.getId());
            return;
        }
        if (node instanceof ExtractNode) {
            log.info("start parse node, node id:{}", node.getId());
            String sql = genCreateSql(node);
            log.info("node id:{}, create table sql:\n{}", node.getId(), sql);
            registerTableSql(node, sql);
            hasParsedSet.add(node.getId());
        } else {
            Preconditions.checkNotNull(relation, "relation is null");
            for (String upstreamNodeId : relation.getInputs()) {
                if (!hasParsedSet.contains(upstreamNodeId)) {
                    Node upstreamNode = nodeMap.get(upstreamNodeId);
                    Preconditions.checkNotNull(upstreamNode,
                            "can not find any node by node id " + upstreamNodeId);
                    parseNode(upstreamNode, relationMap.get(upstreamNodeId), nodeMap, relationMap);
                }
            }
            if (node instanceof LoadNode) {
                String createSql = genCreateSql(node);
                log.info("node id:{}, create table sql:\n{}", node.getId(), createSql);
                registerTableSql(node, createSql);
                Preconditions.checkState(relation.getInputs().size() == 1,
                        "load node only support one input node");
                LoadNode loadNode = (LoadNode) node;
                String insertSql = genLoadNodeInsertSql(loadNode, nodeMap.get(relation.getInputs().get(0)));
                log.info("node id:{}, insert sql:\n{}", node.getId(), insertSql);
                insertSqls.add(insertSql);
                hasParsedSet.add(node.getId());
            } else if (node instanceof TransformNode) {
                TransformNode transformNode = (TransformNode) node;
                Preconditions.checkNotNull(transformNode.getFieldRelationShips(),
                        "field relations is null");
                Preconditions.checkState(!transformNode.getFieldRelationShips().isEmpty(),
                        "field relations is empty");
                String createSql = genCreateSql(node);
                log.info("node id:{}, create table sql:\n{}", node.getId(), createSql);
                String selectSql;
                if (relation instanceof JoinRelationShip) {
                    // parse join relation ship and generate the transform sql
                    Preconditions.checkState(relation.getInputs().size() > 1,
                            "join must have more than one input nodes");
                    Preconditions.checkState(relation.getOutputs().size() == 1,
                            "join node only support one output node");
                    JoinRelationShip joinRelation = (JoinRelationShip) relation;
                    selectSql = genJoinSelectSql(transformNode, joinRelation, nodeMap);
                } else if (relation instanceof UnionNodeRelationShip) {
                    // parse union relation ship and generate the transform sql
                    Preconditions.checkState(relation.getInputs().size() > 1,
                            "union must have more than one input nodes");
                    Preconditions.checkState(relation.getOutputs().size() == 1,
                            "join node only support one output node");
                    UnionNodeRelationShip unionRelation = (UnionNodeRelationShip) relation;
                    selectSql = genUnionNodeSelectSql(transformNode, unionRelation, nodeMap);
                } else {
                    // parse base relation ship that one to one and generate the transform sql
                    Preconditions.checkState(relation.getInputs().size() == 1,
                            "simple transform only support one input node");
                    Preconditions.checkState(relation.getOutputs().size() == 1,
                            "join node only support one output node");
                    selectSql = genSimpleTransformSelectSql(transformNode, relation, nodeMap);
                }
                log.info("node id:{}, tansform sql:\n{}", node.getId(), selectSql);
                registerTableSql(node, createSql + " AS\n" + selectSql);
                hasParsedSet.add(node.getId());
            }
        }
        log.info("parse node success, node id:{}", node.getId());
    }

    /**
     * generate transform sql
     *
     * @param transformNode The transform node
     * @param unionRelation The union relation of sql
     * @param nodeMap Store the mapping relationship between node id and node
     * @return Transform sql for this transform logic
     */
    private String genUnionNodeSelectSql(TransformNode transformNode,
            UnionNodeRelationShip unionRelation, Map<String, Node> nodeMap) {
        throw new UnsupportedOperationException("Union is not currently supported");
    }

    private String genJoinSelectSql(TransformNode node,
            JoinRelationShip relation, Map<String, Node> nodeMap) {
        // Get tablename alias map by input nodes
        Map<String, String> tableNameAliasMap = new HashMap<>(relation.getInputs().size());
        relation.getInputs().forEach(s -> {
            Node inputNode = nodeMap.get(s);
            Preconditions.checkNotNull(inputNode, String.format("input node is not found by id:%s", s));
            tableNameAliasMap.put(s, String.format("t%s", s));
        });
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ");
        Map<String, FieldRelationShip> fieldRelationMap = new HashMap<>(node.getFieldRelationShips().size());
        // Generate mapping for output field to FieldRelationShip
        node.getFieldRelationShips().forEach(s -> {
            fillOutTableNameAlias(Collections.singletonList(s.getInputField()), tableNameAliasMap);
            fieldRelationMap.put(s.getOutputField().getName(), s);
        });
        parseFieldRelations(node.getFields(), fieldRelationMap, sb);
        if (node instanceof DistinctNode) {
            DistinctNode distinctNode = (DistinctNode) node;
            // Fill out the tablename alias for param
            List<FunctionParam> params = new ArrayList<>(distinctNode.getDistinctFields());
            params.add(distinctNode.getOrderField());
            fillOutTableNameAlias(params, tableNameAliasMap);
            // Generate distinct sql, such as ROW_NUMBER()...
            genDistinctSql(distinctNode, sb);
        }
        sb.append(" FROM `").append(nodeMap.get(relation.getInputs().get(0)).genTableName()).append("` ")
                .append(tableNameAliasMap.get(relation.getInputs().get(0)));
        // Parse condition map of join and format condition to sql, such as on 1 = 1...
        String relationFormat = relation.format();
        Map<String, List<FilterFunction>> conditionMap = relation.getJoinConditionMap();
        for (int i = 1; i < relation.getInputs().size(); i++) {
            String inputId = relation.getInputs().get(i);
            sb.append("\n      ").append(relationFormat).append(" ")
                    .append(nodeMap.get(inputId).genTableName()).append(" ")
                    .append(tableNameAliasMap.get(inputId)).append("\n    ON ");
            List<FilterFunction> conditions = conditionMap.get(inputId);
            Preconditions.checkNotNull(conditions, String.format("join condition is null for node id:%s", inputId));
            for (FilterFunction filter : conditions) {
                // Fill out the tablename alias for param
                fillOutTableNameAlias(filter.getParams(), tableNameAliasMap);
                sb.append(" ").append(filter.format());
            }
        }
        if (node.getFilters() != null && !node.getFilters().isEmpty()) {
            // Fill out the tablename alias for param
            fillOutTableNameAlias(new ArrayList<>(node.getFilters()), tableNameAliasMap);
            // Parse filter fields to generate filter sql like 'WHERE 1=1...'
            parseFilterFields(node.getFilters(), sb);
        }
        if (node instanceof DistinctNode) {
            // Generate distinct filter sql like 'WHERE row_num = 1'
            sb = genDistinctFilterSql(node.getFields(), sb);
        }
        return sb.toString();
    }

    /**
     * Fill out the tablename alias
     *
     * @param params The params used in filter, join condition, transform function etc.
     * @param tableNameAliasMap The tablename alias map,
     *         contains all tablename alias used in this relationship of nodes
     */
    private void fillOutTableNameAlias(List<FunctionParam> params, Map<String, String> tableNameAliasMap) {
        for (FunctionParam param : params) {
            if (param instanceof Function) {
                fillOutTableNameAlias(((Function) param).getParams(), tableNameAliasMap);
            } else if (param instanceof FieldInfo) {
                FieldInfo fieldParam = (FieldInfo) param;
                Preconditions.checkNotNull(fieldParam.getNodeId(),
                        "node id of field is null when exists more than two input nodes");
                String tableNameAlias = tableNameAliasMap.get(fieldParam.getNodeId());
                Preconditions.checkNotNull(tableNameAlias,
                        String.format("can not find any node by node id:%s of field:%s",
                                fieldParam.getNodeId(), fieldParam.getName()));
                fieldParam.setTableNameAlias(tableNameAlias);
            }
        }
    }

    /**
     * Generate filter sql of distinct node
     *
     * @param fields The fields of node
     * @param sb Container for storing sql
     * @return A new container for storing sql
     */
    private StringBuilder genDistinctFilterSql(List<FieldInfo> fields, StringBuilder sb) {
        String subSql = sb.toString();
        sb = new StringBuilder("SELECT ");
        for (FieldInfo field : fields) {
            sb.append("\n    `").append(field.getName()).append("`,");
        }
        sb.deleteCharAt(sb.length() - 1).append("\n    FROM (").append(subSql)
                .append(")\nWHERE row_num = 1");
        return sb;
    }

    /**
     * Generate distinct sql according to the deduplication field, the sorting field.
     *
     * @param distinctNode The distinct node
     * @param sb Container for storing sql
     */
    private void genDistinctSql(DistinctNode distinctNode, StringBuilder sb) {
        Preconditions.checkNotNull(distinctNode.getDistinctFields(), "distinctField is null");
        Preconditions.checkState(!distinctNode.getDistinctFields().isEmpty(),
                "distinctField is empty");
        Preconditions.checkNotNull(distinctNode.getOrderField(), "orderField is null");
        sb.append(",\n    ROW_NUMBER() OVER (PARTITION BY ");
        for (FieldInfo distinctField : distinctNode.getDistinctFields()) {
            sb.append(distinctField.format()).append(",");
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.append(" ORDER BY ").append(distinctNode.getOrderField().format()).append(" ")
                .append(distinctNode.getOrderDirection().name()).append(") AS row_num");
    }

    /**
     * Generate the most basic conversion sql one-to-one
     *
     * @param node The transform node
     * @param relation Define relationships between nodes, it also shows the data flow
     * @param nodeMap Store the mapping relationship between node id and node
     * @return Transform sql for this transform logic
     */
    private String genSimpleTransformSelectSql(TransformNode node,
            NodeRelationShip relation, Map<String, Node> nodeMap) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ");
        Map<String, FieldRelationShip> fieldRelationMap = new HashMap<>(node.getFieldRelationShips().size());
        node.getFieldRelationShips().forEach(s -> {
            fieldRelationMap.put(s.getOutputField().getName(), s);
        });
        parseFieldRelations(node.getFields(), fieldRelationMap, sb);
        if (node instanceof DistinctNode) {
            genDistinctSql((DistinctNode) node, sb);
        }
        sb.append("\n    FROM `").append(nodeMap.get(relation.getInputs().get(0)).genTableName()).append("` ");
        parseFilterFields(node.getFilters(), sb);
        if (node instanceof DistinctNode) {
            sb = genDistinctFilterSql(node.getFields(), sb);
        }
        return sb.toString();
    }

    /**
     * Parse filter fields to generate filter sql like 'where 1=1...'
     *
     * @param filters The filter functions
     * @param sb Container for storing sql
     */
    private void parseFilterFields(List<FilterFunction> filters, StringBuilder sb) {
        if (filters != null && !filters.isEmpty()) {
            sb.append("\n    WHERE");
            for (FilterFunction filter : filters) {
                sb.append(" ").append(filter.format());
            }
        }
    }

    /**
     * Parse field relation
     *
     * @param fields The fields defined in node
     * @param fieldRelationMap The field relation map
     * @param sb Container for storing sql
     */
    private void parseFieldRelations(List<FieldInfo> fields,
            Map<String, FieldRelationShip> fieldRelationMap, StringBuilder sb) {
        for (FieldInfo field : fields) {
            FieldRelationShip fieldRelation = fieldRelationMap.get(field.getName());
            if (fieldRelation != null) {
                sb.append("\n    ").append(fieldRelation.getInputField().format())
                        .append(" AS ").append(field.format()).append(",");
            } else {
                String targetType = TableFormatUtils.deriveLogicalType(field.getFormatInfo()).asSummaryString();
                sb.append("\n    CAST(NULL as ").append(targetType).append(") AS ").append(field.format()).append(",");
            }
        }
        sb.deleteCharAt(sb.length() - 1);
    }

    /**
     * Generate load node insert sql
     *
     * @param loadNode The real data write node
     * @param inputNode The input node
     * @return Insert sql
     */
    private String genLoadNodeInsertSql(LoadNode loadNode, Node inputNode) {
        Preconditions.checkNotNull(loadNode.getFieldRelationShips(), "field relations is null");
        Preconditions.checkState(!loadNode.getFieldRelationShips().isEmpty(),
                "field relations is empty");
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO `").append(loadNode.genTableName()).append("` ");
        sb.append("\n    SELECT ");
        Map<String, FieldRelationShip> fieldRelationMap = new HashMap<>(loadNode.getFieldRelationShips().size());
        loadNode.getFieldRelationShips().forEach(s -> {
            fieldRelationMap.put(s.getOutputField().getName(), s);
        });
        parseFieldRelations(loadNode.getFields(), fieldRelationMap, sb);
        sb.append("\n    FROM `").append(inputNode.genTableName()).append("`");
        parseFilterFields(loadNode.getFilters(), sb);
        return sb.toString();
    }

    /**
     * Generate create sql
     *
     * @param node The abstract of extract, transform, load
     * @return The create sql pf table
     */
    private String genCreateSql(Node node) {
        if (node instanceof TransformNode) {
            return genCreateTransformSql(node);
        }
        StringBuilder sb = new StringBuilder("CREATE TABLE `");
        sb.append(node.genTableName()).append("`(\n");
        sb.append(genPrimaryKey(node.getPrimaryKey()));
        sb.append(parseFields(node.getFields(), node));
        if (node instanceof ExtractNode) {
            ExtractNode extractNode = (ExtractNode) node;
            if (extractNode.getWatermarkField() != null) {
                sb.append(",\n     ").append(extractNode.getWatermarkField().format());
            }
        }
        sb.append(")");
        if (node.getPartitionFields() != null && !node.getPartitionFields().isEmpty()) {
            sb.append(String.format("\nPARTITIONED BY (%s)",
                    StringUtils.join(formatFields(node.getPartitionFields()), ",")));
        }
        sb.append(parseOptions(node.tableOptions()));
        return sb.toString();
    }

    /**
     * Genrate create transform sql
     *
     * @param node The transform node
     * @return The create sql of transform node
     */
    private String genCreateTransformSql(Node node) {
        return String.format("CREATE VIEW `%s` (%s)",
                node.genTableName(), parseTransformNodeFields(node.getFields()));
    }

    /**
     * Parse options to generate with options
     *
     * @param options The options defined in node
     * @return The with option string
     */
    private String parseOptions(Map<String, String> options) {
        StringBuilder sb = new StringBuilder();
        if (options != null && !options.isEmpty()) {
            sb.append("\n    WITH (");
            for (Map.Entry<String, String> kv : options.entrySet()) {
                sb.append("\n    '").append(kv.getKey()).append("' = '").append(kv.getValue()).append("'").append(",");
            }
            if (sb.length() > 0) {
                sb.delete(sb.lastIndexOf(","), sb.length());
            }
            sb.append("\n)");
        }
        return sb.toString();
    }

    /**
     * Parse transform node fields
     *
     * @param fields The fields defined in node
     * @return Field format in select sql
     */
    private String parseTransformNodeFields(List<FieldInfo> fields) {
        StringBuilder sb = new StringBuilder();
        for (FieldInfo field : fields) {
            sb.append("\n    `").append(field.getName()).append("`,");
        }
        if (sb.length() > 0) {
            sb.delete(sb.lastIndexOf(","), sb.length());
        }
        return sb.toString();
    }

    /**
     * Parse fields
     *
     * @param fields The fields defined in node
     * @param node The abstract of extract, transform, load
     * @return Field format in select sql
     */
    private String parseFields(List<FieldInfo> fields, Node node) {
        StringBuilder sb = new StringBuilder();
        for (FieldInfo field : fields) {
            sb.append("    `").append(field.getName()).append("` ");
            if (field instanceof BuiltInFieldInfo) {
                BuiltInFieldInfo builtInFieldInfo = (BuiltInFieldInfo) field;
                parseMetaField(node, builtInFieldInfo, sb);
            } else {
                sb.append(TableFormatUtils.deriveLogicalType(field.getFormatInfo()).asSummaryString());
            }
            sb.append(",\n");
        }
        if (sb.length() > 0) {
            sb.delete(sb.lastIndexOf(","), sb.length());
        }
        return sb.toString();
    }

    private void parseMetaField(Node node, BuiltInFieldInfo metaField, StringBuilder sb) {
        if (metaField.getBuiltInField() == BuiltInField.PROCESS_TIME) {
            sb.append(" AS PROCTIME()");
            return;
        }
        if (node instanceof MySqlExtractNode) {
            sb.append(parseMySqlExtractNodeMetaField(metaField));
        } else if (node instanceof KafkaExtractNode) {
            sb.append(parseKafkaExtractNodeMetaField(metaField));
        } else if (node instanceof KafkaLoadNode) {
            sb.append(parseKafkaLoadNodeMetaField(metaField));
        } else {
            throw new UnsupportedOperationException(
                    String.format("This node:%s does not currently support metadata fields",
                            node.getClass().getName()));
        }
    }

    private String parseKafkaLoadNodeMetaField(BuiltInFieldInfo metaField) {
        String metaType;
        switch (metaField.getBuiltInField()) {
            case MYSQL_METADATA_TABLE:
                metaType = "STRING METADATA FROM 'value.table'";
                break;
            case MYSQL_METADATA_DATABASE:
                metaType = "STRING METADATA FROM 'value.database'";
                break;
            case MYSQL_METADATA_EVENT_TIME:
                metaType = "TIMESTAMP(3) METADATA FROM 'value.op_ts'";
                break;
            case MYSQL_METADATA_EVENT_TYPE:
                metaType = "STRING METADATA FROM 'value.op_type'";
                break;
            case MYSQL_METADATA_DATA:
                metaType = "STRING METADATA FROM 'value.data'";
                break;
            case MYSQL_METADATA_IS_DDL:
                metaType = "BOOLEAN METADATA FROM 'value.is_ddl'";
                break;
            case METADATA_TS:
                metaType = "TIMESTAMP_LTZ(3) METADATA FROM 'value.ts'";
                break;
            case METADATA_SQL_TYPE:
                metaType = "MAP<STRING, INT> METADATA FROM 'value.sql_type'";
                break;
            case METADATA_MYSQL_TYPE:
                metaType = "MAP<STRING, STRING> METADATA FROM 'value.mysql_type'";
                break;
            case METADATA_PK_NAMES:
                metaType = "ARRAY<STRING> METADATA FROM 'value.pk_names'";
                break;
            case METADATA_BATCH_ID:
                metaType = "BIGINT METADATA FROM 'value.batch_id'";
                break;
            case METADATA_UPDATE_BEFORE:
                metaType = "ARRAY<MAP<STRING, STRING>> METADATA FROM 'value.update_before'";
                break;
            default:
                metaType = TableFormatUtils.deriveLogicalType(metaField.getFormatInfo()).asSummaryString();
        }
        return metaType;
    }

    private String parseKafkaExtractNodeMetaField(BuiltInFieldInfo metaField) {
        String metaType;
        switch (metaField.getBuiltInField()) {
            case MYSQL_METADATA_TABLE:
                metaType = "STRING METADATA FROM 'value.table'";
                break;
            case MYSQL_METADATA_DATABASE:
                metaType = "STRING METADATA FROM 'value.database'";
                break;
            case METADATA_SQL_TYPE:
                metaType = "MAP<STRING, INT> METADATA FROM 'value.sql-type'";
                break;
            case METADATA_PK_NAMES:
                metaType = "ARRAY<STRING> METADATA FROM 'value.pk-names'";
                break;
            case METADATA_TS:
                metaType = "TIMESTAMP_LTZ(3) METADATA FROM 'value.ingestion-timestamp'";
                break;
            case MYSQL_METADATA_EVENT_TIME:
                metaType = "TIMESTAMP_LTZ(3) METADATA FROM 'value.event-timestamp'";
                break;
            // additional metadata
            case MYSQL_METADATA_EVENT_TYPE:
                metaType = "STRING METADATA FROM 'value.op-type'";
                break;
            case MYSQL_METADATA_IS_DDL:
                metaType = "BOOLEAN METADATA FROM 'value.is-ddl'";
                break;
            case METADATA_MYSQL_TYPE:
                metaType = "MAP<STRING, STRING> METADATA FROM 'value.mysql-type'";
                break;
            case METADATA_BATCH_ID:
                metaType = "BIGINT METADATA FROM 'value.batch-id'";
                break;
            case METADATA_UPDATE_BEFORE:
                metaType = "ARRAY<MAP<STRING, STRING>> METADATA FROM 'value.update-before'";
                break;
            default:
                metaType = TableFormatUtils.deriveLogicalType(metaField.getFormatInfo()).asSummaryString();
        }
        return metaType;
    }

    private String parseMySqlExtractNodeMetaField(BuiltInFieldInfo metaField) {
        String metaType;
        switch (metaField.getBuiltInField()) {
            case MYSQL_METADATA_TABLE:
                metaType = "STRING METADATA FROM 'meta.table_name' VIRTUAL";
                break;
            case MYSQL_METADATA_DATABASE:
                metaType = "STRING METADATA FROM 'meta.database_name' VIRTUAL";
                break;
            case MYSQL_METADATA_EVENT_TIME:
                metaType = "TIMESTAMP(3) METADATA FROM 'meta.op_ts' VIRTUAL";
                break;
            case MYSQL_METADATA_EVENT_TYPE:
                metaType = "STRING METADATA FROM 'meta.op_type' VIRTUAL";
                break;
            case MYSQL_METADATA_DATA:
                metaType = "STRING METADATA FROM 'meta.data' VIRTUAL";
                break;
            case MYSQL_METADATA_IS_DDL:
                metaType = "BOOLEAN METADATA FROM 'meta.is_ddl' VIRTUAL";
                break;
            case METADATA_TS:
                metaType = "TIMESTAMP_LTZ(3) METADATA FROM 'meta.ts' VIRTUAL";
                break;
            case METADATA_SQL_TYPE:
                metaType = "MAP<STRING, INT> METADATA FROM 'meta.sql_type' VIRTUAL";
                break;
            case METADATA_MYSQL_TYPE:
                metaType = "MAP<STRING, STRING> METADATA FROM 'meta.mysql_type' VIRTUAL";
                break;
            case METADATA_PK_NAMES:
                metaType = "ARRAY<STRING> METADATA FROM 'meta.pk_names' VIRTUAL";
                break;
            case METADATA_BATCH_ID:
                metaType = "BIGINT METADATA FROM 'meta.batch_id' VIRTUAL";
                break;
            case METADATA_UPDATE_BEFORE:
                metaType = "ARRAY<MAP<STRING, STRING>> METADATA FROM 'meta.update_before' VIRTUAL";
                break;
            default:
                metaType = TableFormatUtils.deriveLogicalType(metaField.getFormatInfo()).asSummaryString();
        }
        return metaType;
    }

    /**
     * Generate primary key format in sql
     *
     * @param primaryKey The primary key of table
     * @return Primary key format in sql
     */
    private String genPrimaryKey(String primaryKey) {
        if (StringUtils.isNotBlank(primaryKey)) {
            primaryKey = String.format("    PRIMARY KEY (%s) NOT ENFORCED,\n",
                    StringUtils.join(formatFields(primaryKey.split(",")), ","));
        } else {
            primaryKey = "";
        }
        return primaryKey;
    }

    /**
     * Format fields with '`'
     *
     * @param fields The fields that need format
     * @return Field list after format
     */
    private List<String> formatFields(String... fields) {
        List<String> formatFields = new ArrayList<>(fields.length);
        for (String field : fields) {
            if (!field.contains("`")) {
                formatFields.add(String.format("`%s`", field.trim()));
            } else {
                formatFields.add(field);
            }
        }
        return formatFields;
    }

    private List<String> formatFields(List<FieldInfo> fields) {
        List<String> formatFields = new ArrayList<>(fields.size());
        for (FieldInfo field : fields) {
            formatFields.add(field.format());
        }
        return formatFields;
    }
}
