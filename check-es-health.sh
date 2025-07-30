#!/bin/bash

# Simple health check script for Elasticsearch instances
# Used for debugging GitHub Actions integration tests

echo "=== Elasticsearch Health Check ==="

# Check ES8
echo "Checking ES8 at localhost:9200..."
if curl -s http://localhost:9200/_cluster/health > /dev/null 2>&1; then
    echo "✅ ES8 is responding"
    curl -s http://localhost:9200/_cluster/health | grep -o '"status":"[^"]*"' || echo "No status found"
else
    echo "❌ ES8 is not responding"
fi

# Check ES9  
echo "Checking ES9 at localhost:9201..."
if curl -s http://localhost:9201/_cluster/health > /dev/null 2>&1; then
    echo "✅ ES9 is responding"
    curl -s http://localhost:9201/_cluster/health | grep -o '"status":"[^"]*"' || echo "No status found"
else
    echo "❌ ES9 is not responding"
fi

echo "=== Health Check Complete ==="