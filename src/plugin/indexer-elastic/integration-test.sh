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

# Integration Test Helper Script for Elasticsearch 8/9 Indexer Plugin
# This script helps manage Docker Compose environment for integration testing

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose-elasticsearch.yml"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log() {
    echo -e "${GREEN}[$(date +'%Y-%m-%d %H:%M:%S')] $1${NC}"
}

warn() {
    echo -e "${YELLOW}[$(date +'%Y-%m-%d %H:%M:%S')] WARNING: $1${NC}"
}

error() {
    echo -e "${RED}[$(date +'%Y-%m-%d %H:%M:%S')] ERROR: $1${NC}"
}

check_prerequisites() {
    log "Checking prerequisites..."
    
    if ! command -v docker &> /dev/null; then
        error "Docker is not installed or not in PATH"
        exit 1
    fi
    
    if ! docker compose version &> /dev/null; then
        error "Docker Compose is not available"
        exit 1
    fi
    
    if ! docker info &> /dev/null; then
        error "Docker daemon is not running"
        exit 1
    fi
    
    log "Prerequisites check passed"
}

start_elasticsearch() {
    log "Starting Elasticsearch containers..."
    
    cd "$SCRIPT_DIR"
    docker compose -f docker-compose-elasticsearch.yml up -d
    
    log "Waiting for Elasticsearch instances to be ready..."
    
    # Wait for ES 8
    log "Waiting for Elasticsearch 8 (port 9200)..."
    wait_for_elasticsearch "localhost" "9200" "8"
    
    # Wait for ES 9  
    log "Waiting for Elasticsearch 9 (port 9201)..."
    wait_for_elasticsearch "localhost" "9201" "9"
    
    log "All Elasticsearch instances are ready!"
}

wait_for_elasticsearch() {
    local host=$1
    local port=$2
    local version=$3
    local max_attempts=60
    local attempt=1
    
    while [ $attempt -le $max_attempts ]; do
        if curl -s "http://$host:$port/_cluster/health" &> /dev/null; then
            log "Elasticsearch $version is ready at $host:$port"
            return 0
        fi
        
        if [ $((attempt % 10)) -eq 0 ]; then
            log "Still waiting for Elasticsearch $version... (attempt $attempt/$max_attempts)"
        fi
        
        sleep 2
        ((attempt++))
    done
    
    error "Elasticsearch $version did not become ready within $((max_attempts * 2)) seconds"
    return 1
}

stop_elasticsearch() {
    log "Stopping Elasticsearch containers..."
    
    cd "$SCRIPT_DIR"
    docker compose -f docker-compose-elasticsearch.yml down -v
    
    log "Elasticsearch containers stopped and volumes removed"
}

show_status() {
    log "Checking Elasticsearch container status..."
    
    cd "$SCRIPT_DIR"
    docker compose -f docker-compose-elasticsearch.yml ps
    
    echo ""
    log "Health checks:"
    
    # Check ES 8
    if curl -s "http://localhost:9200/_cluster/health" &> /dev/null; then
        echo -e "  ES 8 (port 9200): ${GREEN}✓ Healthy${NC}"
        curl -s "http://localhost:9200/_cluster/health?pretty" | grep -E "(cluster_name|status|number_of_nodes)"
    else
        echo -e "  ES 8 (port 9200): ${RED}✗ Not reachable${NC}"
    fi
    
    # Check ES 9
    if curl -s "http://localhost:9201/_cluster/health" &> /dev/null; then
        echo -e "  ES 9 (port 9201): ${GREEN}✓ Healthy${NC}"
        curl -s "http://localhost:9201/_cluster/health?pretty" | grep -E "(cluster_name|status|number_of_nodes)"
    else
        echo -e "  ES 9 (port 9201): ${RED}✗ Not reachable${NC}"
    fi
}

run_integration_tests() {
    log "Running integration tests..."
    
    cd "$SCRIPT_DIR/.."
    ant integration-test
}

cleanup() {
    log "Cleaning up test environment..."
    stop_elasticsearch
}

show_help() {
    echo "Integration Test Helper for Elasticsearch 8/9 Indexer Plugin"
    echo ""
    echo "Usage: $0 [COMMAND]"
    echo ""
    echo "Commands:"
    echo "  start      Start Elasticsearch containers"
    echo "  stop       Stop Elasticsearch containers"
    echo "  status     Show container status and health"
    echo "  test       Start containers and run integration tests"
    echo "  cleanup    Stop containers and clean up"
    echo "  help       Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 start               # Start ES containers"
    echo "  $0 test                # Run full integration test suite"
    echo "  $0 cleanup             # Stop and clean up everything"
}

main() {
    case "${1:-help}" in
        "start")
            check_prerequisites
            start_elasticsearch
            ;;
        "stop")
            check_prerequisites
            stop_elasticsearch
            ;;
        "status")
            check_prerequisites
            show_status
            ;;
        "test")
            check_prerequisites
            start_elasticsearch
            trap cleanup EXIT
            run_integration_tests
            ;;
        "cleanup")
            check_prerequisites
            cleanup
            ;;
        "help"|*)
            show_help
            ;;
    esac
}

main "$@"