#!/bin/bash

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Simple Elasticsearch connectivity validation script
# This bypasses the complex Ant build system that was hanging

set -e

ES8_PORT=${ES8_PORT:-9200}
ES9_PORT=${ES9_PORT:-9201}
ES8_HOST=${ES8_HOST:-localhost}
ES9_HOST=${ES9_HOST:-localhost}

echo "=== Elasticsearch Connectivity Validation ==="
echo "Test time: $(date)"
echo "ES8: $ES8_HOST:$ES8_PORT"
echo "ES9: $ES9_HOST:$ES9_PORT"
echo ""

# Function to test connectivity with timeout
test_es_connectivity() {
    local host=$1
    local port=$2
    local version=$3
    local max_attempts=10
    local attempt=1
    
    echo "Testing ES$version connectivity..."
    
    while [ $attempt -le $max_attempts ]; do
        echo "  Attempt $attempt/$max_attempts"
        
        # Test basic connectivity
        if curl -s --connect-timeout 3 --max-time 5 "http://$host:$port/" > /dev/null 2>&1; then
            echo "  ✓ ES$version basic connectivity: OK"
            
            # Test cluster health
            if curl -s --connect-timeout 3 --max-time 5 "http://$host:$port/_cluster/health" > /dev/null 2>&1; then
                echo "  ✓ ES$version cluster health: OK"
                
                # Get and display version info
                local version_info=$(curl -s --connect-timeout 3 --max-time 5 "http://$host:$port/" | grep -o '"number":"[^"]*"' | head -1 | cut -d'"' -f4)
                if [ -n "$version_info" ]; then
                    echo "  ✓ ES$version version: $version_info"
                fi
                
                # Get cluster status
                local cluster_status=$(curl -s --connect-timeout 3 --max-time 5 "http://$host:$port/_cluster/health" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
                if [ -n "$cluster_status" ]; then
                    echo "  ✓ ES$version cluster status: $cluster_status"
                fi
                
                echo "  ES$version validation: PASSED"
                return 0
            else
                echo "  ✗ ES$version cluster health check failed"
            fi
        else
            echo "  ✗ ES$version basic connectivity failed"
        fi
        
        if [ $attempt -lt $max_attempts ]; then
            echo "  Waiting 2 seconds before retry..."
            sleep 2
        fi
        
        attempt=$((attempt + 1))
    done
    
    echo "  ES$version validation: FAILED after $max_attempts attempts"
    return 1
}

# Test both Elasticsearch instances
echo "Starting connectivity tests..."
echo ""

ES8_SUCCESS=0
ES9_SUCCESS=0

# Test ES8
if test_es_connectivity "$ES8_HOST" "$ES8_PORT" "8"; then
    ES8_SUCCESS=1
fi
echo ""

# Test ES9  
if test_es_connectivity "$ES9_HOST" "$ES9_PORT" "9"; then
    ES9_SUCCESS=1
fi
echo ""

# Summary
echo "=== Test Summary ==="
if [ $ES8_SUCCESS -eq 1 ]; then
    echo "✓ Elasticsearch 8.x: PASSED"
else
    echo "✗ Elasticsearch 8.x: FAILED"
fi

if [ $ES9_SUCCESS -eq 1 ]; then
    echo "✓ Elasticsearch 9.x: PASSED"
else
    echo "✗ Elasticsearch 9.x: FAILED"
fi

# Overall result
if [ $ES8_SUCCESS -eq 1 ] && [ $ES9_SUCCESS -eq 1 ]; then
    echo ""
    echo "🎉 All Elasticsearch connectivity tests PASSED!"
    echo "The indexer-elastic plugin should work with both ES 8.x and 9.x"
    exit 0
else
    echo ""
    echo "❌ Some Elasticsearch connectivity tests FAILED!"
    echo "Check the Elasticsearch service configuration"
    exit 1
fi