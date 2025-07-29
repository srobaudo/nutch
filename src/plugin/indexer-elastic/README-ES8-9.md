<!--
 Licensed to the Apache Software Foundation (ASF) under one or more
 contributor license agreements.  See the NOTICE file distributed with
 this work for additional information regarding copyright ownership.
 The ASF licenses this file to You under the Apache License, Version 2.0
 (the "License"); you may not use this file except in compliance with
 the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

# Elasticsearch 8/9 Support in Nutch

This document describes the Elasticsearch indexer plugin's support for Elasticsearch versions 8 and 9.

## Overview

The Nutch indexer-elastic plugin has been upgraded to support **Elasticsearch 8.x and 9.x** using the modern Elasticsearch Java client. This represents a significant improvement over the previous implementation that used the deprecated RestHighLevelClient.

## Key Changes

### 1. Updated Dependencies
- **New**: `co.elastic.clients:elasticsearch-java:8.11.0` (official ES Java client)
- **Removed**: `elasticsearch-rest-high-level-client` (deprecated in ES 7.15, removed in 8.0)
- **Added**: Jackson dependencies for modern JSON handling
- **Updated**: HTTP client dependencies to latest versions

### 2. API Modernization
- **ElasticsearchClient**: Replaced RestHighLevelClient with the new type-safe client
- **BulkOperation**: Updated to use the new bulk operation API
- **JSON Handling**: Replaced XContentBuilder with Jackson ObjectMapper
- **Error Handling**: Improved exception handling with ElasticsearchException

### 3. Benefits
- ✅ **Forward Compatibility**: Works with Elasticsearch 8.x and 9.x
- ✅ **Type Safety**: Modern Java client with better type checking
- ✅ **Performance**: Improved bulk operation handling
- ✅ **Maintainability**: Cleaner, more modern codebase
- ✅ **Future-Proof**: Uses officially recommended client

## Compatibility Matrix

| Elasticsearch Version | Support Status | Notes |
|----------------------|----------------|-------|
| 7.x                  | ❌ Not Supported | Use previous plugin version |
| 8.x                  | ✅ Fully Supported | Recommended |
| 9.x                  | ✅ Fully Supported | Latest version |

## Configuration

The configuration parameters remain unchanged for backward compatibility:

```xml
<writer id="indexer_elastic_1" class="org.apache.nutch.indexwriter.elastic.ElasticIndexWriter">
  <parameters>
    <param name="host" value="localhost"/>
    <param name="port" value="9200"/>
    <param name="scheme" value="http"/>
    <param name="index" value="nutch"/>
    <param name="auth" value="false"/>
    <param name="username" value="elastic"/>
    <param name="password" value=""/>
    <param name="max.bulk.docs" value="250"/>
    <param name="max.bulk.size" value="2500500"/>
  </parameters>
</writer>
```

## Usage

### Basic Usage
The plugin works out of the box with Elasticsearch 8 or 9. Simply configure your Elasticsearch connection in `index-writers.xml` and run Nutch indexing commands as usual.

### Authentication
For Elasticsearch clusters with authentication enabled:

```xml
<param name="auth" value="true"/>
<param name="username" value="your_username"/>
<param name="password" value="your_password"/>
```

### HTTPS
For secure connections:

```xml
<param name="scheme" value="https"/>
```

## Performance Tuning

The plugin includes several parameters for optimizing bulk operations:

- `max.bulk.docs`: Maximum number of documents per bulk request (default: 250)
- `max.bulk.size`: Maximum size of bulk request in bytes (default: 2500500)
- `bulk.close.timeout`: Timeout for closing bulk operations (default: 600 seconds)

## Troubleshooting

### Common Issues

1. **Connection refused**: Ensure Elasticsearch is running and accessible
2. **Authentication failures**: Verify username/password and ensure authentication is enabled
3. **SSL/TLS issues**: Check certificate configuration for HTTPS connections

### Logging

Enable debug logging for the elastic indexer:

```xml
<logger name="org.apache.nutch.indexwriter.elastic" level="DEBUG"/>
```

## Migration from ES 7

If you're upgrading from Elasticsearch 7:

1. Upgrade your Elasticsearch cluster to version 8 or 9
2. Update Nutch to use this new plugin version
3. Test indexing with a small dataset before full migration
4. No configuration changes required

## Development Notes

For developers wanting to modify or extend the plugin:

- The main class is `ElasticIndexWriter.java`
- Dependencies are managed in `ivy.xml`
- Plugin metadata is in `plugin.xml`
- Build dependencies with: `ant -f build-ivy.xml`

## Support

This implementation supports all standard Elasticsearch 8 and 9 features including:
- Document indexing and updates
- Bulk operations
- Authentication (basic auth)
- SSL/TLS connections
- Index management
- Error handling and retries