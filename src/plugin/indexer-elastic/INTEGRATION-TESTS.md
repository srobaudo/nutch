# Integration Tests for Elasticsearch 8/9 Indexer Plugin

This directory contains comprehensive integration tests for the Nutch Elasticsearch indexer plugin, validating compatibility with both Elasticsearch 8.x and 9.x using real ES instances.

## Overview

The integration test suite provides end-to-end validation of the ES 8/9 upgrade by:
- Running tests against actual Elasticsearch instances (not mocks)
- Testing both Elasticsearch 8.x and 9.x compatibility
- Validating document indexing, bulk operations, and error handling
- Using Docker Compose for consistent test environments

## Test Structure

### TestElasticIndexWriterIntegration.java
The main integration test class that runs identical test suites against both ES versions:

**Test Methods:**
- `testElasticsearch8Integration()` - Full test suite against ES 8
- `testElasticsearch9Integration()` - Full test suite against ES 9  
- `testDocumentIndexingES8/9()` - Document CRUD operations
- `testBulkOperationsES8/9()` - Bulk indexing validation
- `testErrorHandlingES8/9()` - Error condition handling

**Test Coverage:**
- Client initialization and configuration
- Document indexing and retrieval
- Bulk operations (5 documents per test)
- Multi-value field handling
- Date field processing
- Error condition handling
- Connection management

## Docker Environment

### docker-compose-elasticsearch.yml
Defines two Elasticsearch services:
- **elasticsearch8**: ES 8.11.0 on ports 9200/9300
- **elasticsearch9**: ES 9.0.0 on ports 9201/9301

**Configuration:**
- Single-node clusters for testing
- Security disabled for simplified testing
- Health checks with 60s startup timeout
- Memory limited to 512MB per instance

## Running Integration Tests

### Prerequisites
```bash
# Required tools
docker --version      # Docker 20.x+
docker-compose --version  # Docker Compose 1.28+

# Verify Docker daemon is running
docker info
```

### Method 1: Using Ant (Recommended)
```bash
# From the indexer-elastic plugin directory
cd src/plugin/indexer-elastic

# Run complete integration test suite
ant integration-test

# Run unit tests only
ant test

# Run both unit and integration tests
ant test-all
```

### Method 2: Using Helper Script
```bash
# From the indexer-elastic plugin directory
cd src/plugin/indexer-elastic

# Start ES containers and run tests
./integration-test.sh test

# Just start containers
./integration-test.sh start

# Check container status
./integration-test.sh status

# Stop and cleanup
./integration-test.sh cleanup
```

### Method 3: Manual Steps
```bash
# 1. Start Elasticsearch containers
docker-compose -f docker-compose-elasticsearch.yml up -d

# 2. Wait for containers to be ready (check health)
curl http://localhost:9200/_cluster/health  # ES 8
curl http://localhost:9201/_cluster/health  # ES 9

# 3. Run tests
ant compile-test
ant -Dtestcase=TestElasticIndexWriterIntegration test-plugin

# 4. Cleanup
docker-compose -f docker-compose-elasticsearch.yml down -v
```

## Test Execution Flow

1. **Environment Setup**
   - Validates Docker/Docker Compose availability
   - Starts ES 8 and ES 9 containers
   - Waits for health checks to pass

2. **ES 8 Tests**
   - Configures client for localhost:9200
   - Runs full test suite against ES 8.11.0
   - Validates all operations work correctly

3. **ES 9 Tests**  
   - Reconfigures client for localhost:9201
   - Runs identical test suite against ES 9.0.0
   - Validates forward compatibility

4. **Cleanup**
   - Removes test indices
   - Stops and removes containers
   - Cleans up volumes

## Test Configuration

### Elasticsearch Settings
```yaml
# Both ES instances use:
discovery.type: single-node
xpack.security.enabled: false
ES_JAVA_OPTS: -Xms512m -Xmx512m
```

### Test Index
- **Name**: `nutch-integration-test`
- **Documents**: Various test documents with different field types
- **Cleanup**: Automatically removed after each test run

### Network Configuration
- **ES 8**: localhost:9200 (HTTP), localhost:9300 (Transport)
- **ES 9**: localhost:9201 (HTTP), localhost:9301 (Transport)
- **Network**: Custom bridge network `nutch-test`

## Troubleshooting

### Common Issues

**Docker not available:**
```bash
# Install Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sh get-docker.sh

# Start Docker service
sudo systemctl start docker
```

**Containers fail to start:**
```bash
# Check Docker logs
docker-compose -f docker-compose-elasticsearch.yml logs

# Check available memory
free -h

# Check port conflicts
netstat -tulpn | grep -E ":(9200|9201|9300|9301)"
```

**Tests timeout:**
```bash
# Increase Docker memory
# Edit /etc/docker/daemon.json:
{
  "default-ulimits": {
    "memlock": {
      "hard": -1,
      "soft": -1
    }
  }
}

# Restart Docker
sudo systemctl restart docker
```

**Connection refused:**
```bash
# Wait longer for ES startup
sleep 60

# Check ES health manually
curl -v http://localhost:9200/_cluster/health
curl -v http://localhost:9201/_cluster/health
```

### Debug Mode
```bash
# Run with verbose output
ant -v integration-test

# Check container logs
docker-compose -f docker-compose-elasticsearch.yml logs -f elasticsearch8
docker-compose -f docker-compose-elasticsearch.yml logs -f elasticsearch9
```

## CI/CD Integration

### GitHub Actions Example
```yaml
name: Integration Tests
on: [push, pull_request]

jobs:
  integration-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK 11
        uses: actions/setup-java@v3
        with:
          java-version: '11'
      - name: Run Integration Tests
        run: |
          cd src/plugin/indexer-elastic
          ./integration-test.sh test
```

### Jenkins Pipeline Example
```groovy
pipeline {
    agent any
    stages {
        stage('Integration Tests') {
            steps {
                dir('src/plugin/indexer-elastic') {
                    sh './integration-test.sh test'
                }
            }
            post {
                always {
                    dir('src/plugin/indexer-elastic') {
                        sh './integration-test.sh cleanup'
                    }
                }
            }
        }
    }
}
```

## Performance Considerations

- **Memory**: Each ES instance uses 512MB heap
- **Startup**: ~30-60 seconds for both containers
- **Test Duration**: ~2-3 minutes for full suite
- **Cleanup**: ~10 seconds to stop containers

## Test Data

### Sample Documents
```json
{
  "id": "test-doc-8",
  "title": "Test Document for ES 8", 
  "content": "This is test content for Elasticsearch 8",
  "url": "http://example.com/test-8",
  "tstamp": "2024-01-15T10:30:00Z"
}
```

### Bulk Test Data
- 5 documents per bulk operation test
- Unique IDs per ES version
- Variety of field types and values

## Validation Criteria

✅ **ES 8 Compatibility**: All tests pass against ES 8.11.0
✅ **ES 9 Compatibility**: All tests pass against ES 9.0.0  
✅ **Document Operations**: Index, search, delete work correctly
✅ **Bulk Operations**: Multiple documents indexed successfully
✅ **Error Handling**: Invalid operations handled gracefully
✅ **Configuration**: All connection parameters work
✅ **Cleanup**: Test environment properly cleaned up

This integration test suite provides confidence that the Elasticsearch indexer plugin works correctly with both current (ES 8) and future (ES 9) Elasticsearch versions.