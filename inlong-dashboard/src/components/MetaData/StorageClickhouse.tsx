/*
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

import {
  getColsFromFields,
  GetStorageColumnsType,
  GetStorageFormFieldsType,
} from '@/utils/metaData';
import i18n from '@/i18n';
import { ColumnsType } from 'antd/es/table';
import EditableTable from '@/components/EditableTable';
import { excludeObject } from '@/utils';
import { sourceDataFields } from './SourceDataFields';

// ClickHouse targetType
const clickhouseTargetTypes = [
  'String',
  'Int8',
  'Int16',
  'Int32',
  'Int64',
  'Float32',
  'Float64',
  'DateTime',
  'Date',
].map(item => ({
  label: item,
  value: item,
}));

const getForm: GetStorageFormFieldsType = (
  type: 'form' | 'col' = 'form',
  { currentValues, inlongGroupId, isEdit, dataType } = {} as any,
) => {
  const fileds = [
    {
      name: 'dbName',
      type: 'input',
      label: i18n.t('components.AccessHelper.StorageMetaData.Clickhouse.DbName'),
      rules: [{ required: true }],
      props: {
        disabled: isEdit && [110, 130].includes(currentValues?.status),
      },
      _inTable: true,
    },
    {
      name: 'tableName',
      type: 'input',
      label: i18n.t('components.AccessHelper.StorageMetaData.Clickhouse.TableName'),
      rules: [{ required: true }],
      props: {
        disabled: isEdit && [110, 130].includes(currentValues?.status),
      },
      _inTable: true,
    },
    {
      name: 'enableCreateResource',
      type: 'radio',
      label: i18n.t('components.AccessHelper.StorageMetaData.EnableCreateResource'),
      rules: [{ required: true }],
      initialValue: 1,
      tooltip: i18n.t('components.AccessHelper.StorageMetaData.EnableCreateResourceHelp'),
      props: {
        disabled: isEdit && [110, 130].includes(currentValues?.status),
        options: [
          {
            label: i18n.t('basic.Yes'),
            value: 1,
          },
          {
            label: i18n.t('basic.No'),
            value: 0,
          },
        ],
      },
    },
    {
      name: 'username',
      type: 'input',
      label: i18n.t('components.AccessHelper.StorageMetaData.Username'),
      rules: [{ required: true }],
      props: {
        disabled: isEdit && [110, 130].includes(currentValues?.status),
      },
      _inTable: true,
    },
    {
      name: 'password',
      type: 'password',
      label: i18n.t('components.AccessHelper.StorageMetaData.Password'),
      rules: [{ required: true }],
      props: {
        disabled: isEdit && [110, 130].includes(currentValues?.status),
        style: {
          maxWidth: 500,
        },
      },
    },
    {
      type: 'input',
      label: 'JDBC URL',
      name: 'jdbcUrl',
      rules: [{ required: true }],
      props: {
        placeholder: 'jdbc:clickhouse://127.0.0.1:8123',
        disabled: isEdit && [110, 130].includes(currentValues?.status),
        style: { width: 500 },
      },
    },
    {
      name: 'flushInterval',
      type: 'inputnumber',
      label: i18n.t('components.AccessHelper.StorageMetaData.Clickhouse.FlushInterval'),
      initialValue: 1,
      props: {
        min: 1,
        disabled: isEdit && [110, 130].includes(currentValues?.status),
      },
      rules: [{ required: true }],
      suffix: i18n.t('components.AccessHelper.StorageMetaData.Clickhouse.FlushIntervalUnit'),
    },
    {
      name: 'flushRecord',
      type: 'inputnumber',
      label: i18n.t('components.AccessHelper.StorageMetaData.Clickhouse.FlushRecord'),
      initialValue: 1000,
      props: {
        min: 1,
        disabled: isEdit && [110, 130].includes(currentValues?.status),
      },
      rules: [{ required: true }],
      suffix: i18n.t('components.AccessHelper.StorageMetaData.Clickhouse.FlushRecordUnit'),
    },
    {
      name: 'retryTime',
      type: 'inputnumber',
      label: i18n.t('components.AccessHelper.StorageMetaData.Clickhouse.RetryTimes'),
      initialValue: 3,
      props: {
        min: 1,
        disabled: isEdit && [110, 130].includes(currentValues?.status),
      },
      rules: [{ required: true }],
      suffix: i18n.t('components.AccessHelper.StorageMetaData.Clickhouse.RetryTimesUnit'),
    },
    {
      name: 'isDistributed',
      type: 'radio',
      label: i18n.t('components.AccessHelper.StorageMetaData.Clickhouse.IsDistributed'),
      initialValue: 0,
      props: {
        options: [
          {
            label: i18n.t('components.AccessHelper.StorageMetaData.Clickhouse.Yes'),
            value: 1,
          },
          {
            label: i18n.t('components.AccessHelper.StorageMetaData.Clickhouse.No'),
            value: 0,
          },
        ],
        disabled: isEdit && [110, 130].includes(currentValues?.status),
      },
      rules: [{ required: true }],
    },
    {
      name: 'partitionStrategy',
      type: 'select',
      label: i18n.t('components.AccessHelper.StorageMetaData.Clickhouse.PartitionStrategy'),
      initialValue: 'BALANCE',
      rules: [{ required: true }],
      props: {
        options: [
          {
            label: 'BALANCE',
            value: 'BALANCE',
          },
          {
            label: 'RANDOM',
            value: 'RANDOM',
          },
          {
            label: 'HASH',
            value: 'HASH',
          },
        ],
        disabled: isEdit && [110, 130].includes(currentValues?.status),
      },
      visible: values => values.isDistributed,
      _inTable: true,
    },
    {
      name: 'partitionFields',
      type: 'input',
      label: i18n.t('components.AccessHelper.StorageMetaData.Clickhouse.PartitionFields'),
      rules: [{ required: true }],
      visible: values => values.isDistributed && values.partitionStrategy === 'HASH',
      props: {
        disabled: isEdit && [110, 130].includes(currentValues?.status),
      },
    },
    {
      name: 'fieldList',
      type: EditableTable,
      props: {
        size: 'small',
        editing: ![110, 130].includes(currentValues?.status),
        columns: getFieldListColumns(dataType, currentValues),
      },
    },
  ];

  return type === 'col'
    ? getColsFromFields(fileds)
    : fileds.map(item => excludeObject(['_inTable'], item));
};

const getFieldListColumns: GetStorageColumnsType = (dataType, currentValues) => {
  return [
    ...sourceDataFields,
    {
      title: `ClickHouse${i18n.t('components.AccessHelper.StorageMetaData.Clickhouse.FieldName')}`,
      dataIndex: 'fieldName',
      rules: [
        { required: true },
        {
          pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/,
          message: i18n.t('components.AccessHelper.StorageMetaData.Clickhouse.FieldNameRule'),
        },
      ],
      props: (text, record, idx, isNew) => ({
        disabled: [110, 130].includes(currentValues?.status as number) && !isNew,
      }),
    },
    {
      title: `ClickHouse${i18n.t('components.AccessHelper.StorageMetaData.Clickhouse.FieldType')}`,
      dataIndex: 'fieldType',
      initialValue: clickhouseTargetTypes[0].value,
      type: 'select',
      props: (text, record, idx, isNew) => ({
        disabled: [110, 130].includes(currentValues?.status as number) && !isNew,
        options: clickhouseTargetTypes,
      }),
      rules: [{ required: true }],
    },
    {
      title: `ClickHouse${i18n.t(
        'components.AccessHelper.StorageMetaData.Clickhouse.FieldDescription',
      )}`,
      dataIndex: 'fieldComment',
      props: (text, record, idx, isNew) => ({
        disabled: [110, 130].includes(currentValues?.status as number) && !isNew,
      }),
    },
  ];
};

const tableColumns = getForm('col') as ColumnsType;

export const StorageClickhouse = {
  getForm,
  getFieldListColumns,
  tableColumns,
};
