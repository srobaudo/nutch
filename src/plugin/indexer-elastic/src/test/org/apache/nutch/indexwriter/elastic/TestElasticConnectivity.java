/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nutch.indexwriter.elastic;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.Test;

/**
 * Simple connectivity test for Elasticsearch instances without any Nutch dependencies.
 * This helps isolate connectivity issues from Nutch configuration problems.
 */
public class TestElasticConnectivity {

  private static final String ES8_HOST = "localhost";
  private static final int ES8_PORT = Integer.getInteger("elasticsearch8.port", 9200);
  private static final String ES9_HOST = "localhost";
  private static final int ES9_PORT = Integer.getInteger("elasticsearch9.port", 9201);

  @Test
  public void testES8Connectivity() throws Exception {
    System.out.println("=== Testing ES8 connectivity ===");
    testElasticsearchConnectivity(ES8_HOST, ES8_PORT, "8");
    System.out.println("=== ES8 connectivity test passed ===");
  }

  @Test
  public void testES9Connectivity() throws Exception {
    System.out.println("=== Testing ES9 connectivity ===");
    testElasticsearchConnectivity(ES9_HOST, ES9_PORT, "9");
    System.out.println("=== ES9 connectivity test passed ===");
  }

  private void testElasticsearchConnectivity(String host, int port, String version) throws Exception {
    System.out.printf("Testing Elasticsearch %s at %s:%d%n", version, host, port);
    
    HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
    
    // Test root endpoint
    System.out.println("Testing root endpoint...");
    HttpRequest rootRequest = HttpRequest.newBuilder()
        .uri(URI.create("http://" + host + ":" + port + "/"))
        .timeout(Duration.ofSeconds(5))
        .GET()
        .build();
    
    HttpResponse<String> rootResponse = client.send(rootRequest, HttpResponse.BodyHandlers.ofString());
    System.out.println("Root response status: " + rootResponse.statusCode());
    System.out.println("Root response body: " + rootResponse.body());
    
    assertTrue("Root endpoint should return 200", rootResponse.statusCode() == 200);
    assertTrue("Response should contain version info", rootResponse.body().contains("version"));
    
    // Test cluster health endpoint
    System.out.println("Testing cluster health endpoint...");
    HttpRequest healthRequest = HttpRequest.newBuilder()
        .uri(URI.create("http://" + host + ":" + port + "/_cluster/health"))
        .timeout(Duration.ofSeconds(5))
        .GET()
        .build();
    
    HttpResponse<String> healthResponse = client.send(healthRequest, HttpResponse.BodyHandlers.ofString());
    System.out.println("Health response status: " + healthResponse.statusCode());
    System.out.println("Health response body: " + healthResponse.body());
    
    assertTrue("Health endpoint should return 200", healthResponse.statusCode() == 200);
    assertTrue("Response should contain cluster status", 
               healthResponse.body().contains("status") && 
               (healthResponse.body().contains("green") || healthResponse.body().contains("yellow")));
    
    System.out.printf("Elasticsearch %s connectivity test successful%n", version);
  }
}