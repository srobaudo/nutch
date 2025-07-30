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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.nutch.indexer.IndexWriterParams;
import org.apache.nutch.indexer.NutchDocument;
import org.apache.nutch.indexwriter.elastic.ElasticConstants;
import org.apache.nutch.indexwriter.elastic.ElasticIndexWriter;
import org.apache.nutch.util.NutchConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;

/**
 * Integration test class for ElasticIndexWriter against real Elasticsearch instances.
 * Tests the ES 8/9 client upgrade with actual ES containers running via Docker Compose.
 */
public class TestElasticIndexWriterIntegration {

  protected static final Logger LOG = LoggerFactory
      .getLogger(TestElasticIndexWriterIntegration.class);

  private static final String ES8_HOST = "localhost";
  private static final int ES8_PORT = Integer.getInteger("elasticsearch8.port", 9200);
  private static final String ES9_HOST = "localhost";
  private static final int ES9_PORT = Integer.getInteger("elasticsearch9.port", 9201);
  private static final String TEST_INDEX = "nutch-integration-test";
  private static final String SCHEME = "http";
  
  private Configuration conf;
  private ElasticIndexWriter indexWriter;
  private IndexWriterParams params;
  private String currentESVersion;
  private String currentHost;
  private int currentPort;

  @Before
  public void setUp() throws Exception {
    // Fix log4j2 infinite loop by setting system properties early and explicitly
    // Use different property names to avoid circular reference
    System.setProperty("hadoop.log.dir", "/tmp/nutch-test");
    System.setProperty("hadoop.log.file", "integration-test.log");
    
    // Ensure log directory exists
    java.io.File logDir = new java.io.File("/tmp/nutch-test");
    if (!logDir.exists()) {
      logDir.mkdirs();
    }
    
    System.out.println("=== Starting integration test setup ===");
    
    try {
      conf = NutchConfiguration.create();
      indexWriter = new ElasticIndexWriter();
      params = new IndexWriterParams(new HashMap<String, String>());
      
      // Default to ES8 for setup - individual tests will switch versions
      currentESVersion = "8";
      currentHost = ES8_HOST;
      currentPort = ES8_PORT;
      
      System.out.println("=== Integration test setup completed ===");
    } catch (Exception e) {
      System.err.println("ERROR in setUp: " + e.getMessage());
      e.printStackTrace();
      throw e;
    }
  }

  @After
  public void tearDown() throws Exception {
    if (indexWriter != null) {
      try {
        indexWriter.close();
      } catch (IOException e) {
        LOG.warn("Error closing indexWriter: {}", e.getMessage());
      }
    }
    
    // Clean up test indices
    try {
      cleanupTestIndex(ES8_HOST, ES8_PORT);
      cleanupTestIndex(ES9_HOST, ES9_PORT);
    } catch (Exception e) {
      LOG.warn("Error cleaning up test indices: {}", e.getMessage());
    }
  }

  /**
   * Test Elasticsearch 8.x integration
   */
  @Test
  public void testElasticsearch8Integration() throws Exception {
    System.out.println("=== Starting Elasticsearch 8 integration test ===");
    
    try {
      // Step 1: Basic connectivity check
      System.out.println("Step 1: Testing basic connectivity to ES8...");
      if (!isElasticsearchReachable(ES8_HOST, ES8_PORT)) {
        System.err.println("ERROR: ES8 is not reachable at " + ES8_HOST + ":" + ES8_PORT);
        fail("ES8 instance should be reachable at " + ES8_HOST + ":" + ES8_PORT);
      }
      System.out.println("Step 1 completed: ES8 connectivity verified");
      
      // Step 2: Configure IndexWriter with minimal timeout
      System.out.println("Step 2: Configuring IndexWriter for ES8...");
      setupForES8WithTimeout();
      System.out.println("Step 2 completed: IndexWriter configured");
      
      // Step 3: Test very simple document operation
      System.out.println("Step 3: Testing simple document write...");
      testSimpleDocumentWrite();
      System.out.println("Step 3 completed: Document write successful");
      
      System.out.println("=== Elasticsearch 8 integration test completed successfully ===");
    } catch (Exception e) {
      System.err.println("ERROR in ES8 integration test: " + e.getMessage());
      e.printStackTrace();
      throw e;
    }
  }
  
  /**
   * Simple document write test to isolate issues
   */
  private void testSimpleDocumentWrite() throws Exception {
    // Create a very simple test document
    NutchDocument doc = new NutchDocument();
    doc.add("id", "simple-test-" + System.currentTimeMillis());
    doc.add("content", "Simple test content");
    
    System.out.println("Writing simple test document...");
    indexWriter.write(doc);
    System.out.println("Committing document...");
    indexWriter.commit();
    System.out.println("Simple document write completed successfully");
  }

  /**
   * Setup for ES8 with timeout protection
   */
  private void setupForES8WithTimeout() throws Exception {
    currentESVersion = "8";
    currentHost = ES8_HOST;
    currentPort = ES8_PORT;
    
    System.out.println("Configuring IndexWriter for ES8 with timeout protection...");
    Map<String, String> parameters = new HashMap<>();
    parameters.put(ElasticConstants.HOSTS, currentHost);
    parameters.put(ElasticConstants.PORT, String.valueOf(currentPort));
    parameters.put(ElasticConstants.SCHEME, SCHEME);
    parameters.put(ElasticConstants.INDEX, TEST_INDEX);
    parameters.put(ElasticConstants.USE_AUTH, "false");

    params = new IndexWriterParams(parameters);
    
    // Close existing writer if open
    if (indexWriter != null) {
      try {
        indexWriter.close();
      } catch (IOException e) {
        // Ignore
      }
    }
    
    indexWriter = new ElasticIndexWriter();
    indexWriter.setConf(conf);
    
    // Open with timeout protection
    System.out.println("Opening IndexWriter with 10 second timeout...");
    
    // Use a separate thread to detect hanging
    final Exception[] openException = new Exception[1];
    final boolean[] openCompleted = new boolean[1];
    
    Thread openThread = new Thread(() -> {
      try {
        indexWriter.open(params);
        openCompleted[0] = true;
      } catch (Exception e) {
        openException[0] = e;
      }
    });
    
    openThread.start();
    openThread.join(10000); // 10 second timeout
    
    if (!openCompleted[0] && openException[0] == null) {
      openThread.interrupt();
      throw new RuntimeException("IndexWriter.open() timed out after 10 seconds");
    }
    
    if (openException[0] != null) {
      throw openException[0];
    }
    
    System.out.println("IndexWriter opened successfully");
  }

  /**
   * Test Elasticsearch 9.x integration
   */
  @Test
  public void testElasticsearch9Integration() throws Exception {
    LOG.info("=== Starting Elasticsearch 9 integration test ===");
    setupForES9();
    runIntegrationTestSuite();
    LOG.info("=== Elasticsearch 9 integration test completed successfully ===");
  }

  /**
   * Test document indexing and retrieval with ES 8
   */
  @Test
  public void testDocumentIndexingES8() throws Exception {
    setupForES8();
    testDocumentIndexingAndRetrieval();
  }

  /**
   * Test document indexing and retrieval with ES 9
   */
  @Test
  public void testDocumentIndexingES9() throws Exception {
    setupForES9();
    testDocumentIndexingAndRetrieval();
  }

  /**
   * Test bulk operations with ES 8
   */
  @Test
  public void testBulkOperationsES8() throws Exception {
    setupForES8();
    testBulkOperations();
  }

  /**
   * Test bulk operations with ES 9
   */
  @Test
  public void testBulkOperationsES9() throws Exception {
    setupForES9();
    testBulkOperations();
  }

  /**
   * Test error handling with ES 8
   */
  @Test
  public void testErrorHandlingES8() throws Exception {
    setupForES8();
    testErrorHandling();
  }

  /**
   * Test error handling with ES 9
   */
  @Test
  public void testErrorHandlingES9() throws Exception {
    setupForES9();
    testErrorHandling();
  }

  private void setupForES8() throws Exception {
    System.out.println("=== Starting ES8 setup ===");
    currentESVersion = "8";
    currentHost = ES8_HOST;
    currentPort = ES8_PORT;
    
    System.out.println("Setting up integration test for Elasticsearch 8 at " + currentHost + ":" + currentPort);
    LOG.info("Setting up integration test for Elasticsearch 8 at {}:{}", currentHost, currentPort);
    
    // Clean up before testing
    try {
      System.out.println("Attempting cleanup of test index...");
      cleanupTestIndex(currentHost, currentPort);
      Thread.sleep(500);
      System.out.println("Test index cleanup completed");
    } catch (Exception e) {
      System.out.println("Pre-test cleanup failed (may be expected): " + e.getMessage());
      LOG.warn("Pre-test cleanup failed (may be expected): {}", e.getMessage());
    }
    
    System.out.println("Waiting for Elasticsearch 8 to be ready...");
    waitForElasticsearch(currentHost, currentPort);
    System.out.println("Elasticsearch 8 is ready, configuring index writer...");
    
    configureIndexWriter(currentHost, currentPort);
    System.out.println("=== ES8 setup completed ===");
    LOG.info("Setup completed for ES 8");
  }

  private void setupForES9() throws Exception {
    currentESVersion = "9";
    currentHost = ES9_HOST;
    currentPort = ES9_PORT;
    
    LOG.info("Setting up integration test for Elasticsearch 9 at {}:{}", currentHost, currentPort);
    
    // Clean up before testing
    try {
      cleanupTestIndex(currentHost, currentPort);
      Thread.sleep(500);
    } catch (Exception e) {
      LOG.warn("Pre-test cleanup failed (may be expected): {}", e.getMessage());
    }
    
    waitForElasticsearch(currentHost, currentPort);
    configureIndexWriter(currentHost, currentPort);
    LOG.info("Setup completed for ES 9");
  }

  private void configureIndexWriter(String host, int port) throws Exception {
    System.out.println("=== Configuring IndexWriter for " + host + ":" + port + " ===");
    Map<String, String> parameters = new HashMap<>();
    parameters.put(ElasticConstants.HOSTS, host);
    parameters.put(ElasticConstants.PORT, String.valueOf(port));
    parameters.put(ElasticConstants.SCHEME, SCHEME);
    parameters.put(ElasticConstants.INDEX, TEST_INDEX);
    parameters.put(ElasticConstants.USE_AUTH, "false");

    System.out.println("Creating IndexWriterParams with parameters: " + parameters);
    params = new IndexWriterParams(parameters);
    
    // Close existing writer if open
    if (indexWriter != null) {
      try {
        System.out.println("Closing existing IndexWriter...");
        indexWriter.close();
        System.out.println("Existing IndexWriter closed");
      } catch (IOException e) {
        System.out.println("Error closing existing IndexWriter: " + e.getMessage());
        // Ignore
      }
    }
    
    System.out.println("Creating new ElasticIndexWriter...");
    indexWriter = new ElasticIndexWriter();
    System.out.println("Setting configuration...");
    indexWriter.setConf(conf);
    
    System.out.println("Opening IndexWriter with timeout protection...");
    // Add timeout protection for the open() call
    long startTime = System.currentTimeMillis();
    try {
      indexWriter.open(params);
      long duration = System.currentTimeMillis() - startTime;
      System.out.println("IndexWriter opened successfully in " + duration + "ms");
    } catch (Exception e) {
      long duration = System.currentTimeMillis() - startTime;
      System.err.println("IndexWriter open failed after " + duration + "ms: " + e.getMessage());
      e.printStackTrace();
      throw e;
    }
    System.out.println("=== IndexWriter configured successfully ===");
    
    LOG.info("Configured ElasticIndexWriter for ES {} at {}:{}", currentESVersion, host, port);
  }

  private void runIntegrationTestSuite() throws Exception {
    LOG.info("Running integration test suite against Elasticsearch {}", currentESVersion);
    
    // Test basic connectivity
    assertTrue("ES instance should be reachable", isElasticsearchReachable(currentHost, currentPort));
    
    // Test index writer initialization
    assertNotNull("IndexWriter should be initialized", indexWriter);
    
    // Test document operations
    testDocumentIndexingAndRetrieval();
    testBulkOperations();
    testErrorHandling();
    
    LOG.info("Integration test suite completed successfully for ES {}", currentESVersion);
  }

  private void testDocumentIndexingAndRetrieval() throws Exception {
    LOG.info("Testing document indexing and retrieval for ES {}", currentESVersion);
    
    // Create test document
    NutchDocument doc = new NutchDocument();
    doc.add("id", "test-doc-" + currentESVersion);
    doc.add("title", "Test Document for ES " + currentESVersion);
    doc.add("content", "This is test content for Elasticsearch " + currentESVersion);
    doc.add("url", "http://example.com/test-" + currentESVersion);
    doc.add("tstamp", new Date());
    
    // Index the document
    indexWriter.write(doc);
    indexWriter.commit();
    
    // Wait for indexing with progressive delay
    Thread.sleep(1000);
    
    // Create ES client for verification
    ElasticsearchClient client = createESClient(currentHost, currentPort);
    
    try {
      // Try multiple times to account for indexing delay
      boolean documentFound = false;
      for (int attempt = 0; attempt < 10; attempt++) {
        try {
          SearchRequest searchRequest = SearchRequest.of(s -> s
              .index(TEST_INDEX)
              .query(q -> q
                  .match(m -> m
                      .field("id")
                      .query("test-doc-" + currentESVersion)
                  )
              )
          );
          
          SearchResponse<Object> response = client.search(searchRequest, Object.class);
          
          if (response.hits().total().value() > 0) {
            Hit<Object> hit = response.hits().hits().get(0);
            assertNotNull("Hit should not be null", hit);
            documentFound = true;
            break;
          }
          
          LOG.debug("Document not found yet, attempt {}/10", attempt + 1);
          Thread.sleep(1000);
        } catch (Exception e) {
          LOG.debug("Search attempt {} failed: {}", attempt + 1, e.getMessage());
          Thread.sleep(1000);
        }
      }
      
      assertTrue("Document should be found in index after multiple attempts", documentFound);
      
    } finally {
      // Close the client properly
      try {
        client._transport().close();
      } catch (IOException e) {
        LOG.warn("Error closing ES client: {}", e.getMessage());
      }
    }
    
    LOG.info("Document indexing and retrieval test passed for ES {}", currentESVersion);
  }

  private void testBulkOperations() throws Exception {
    LOG.info("Testing bulk operations for ES {}", currentESVersion);
    
    // Create multiple test documents
    for (int i = 0; i < 5; i++) {
      NutchDocument doc = new NutchDocument();
      doc.add("id", "bulk-doc-" + currentESVersion + "-" + i);
      doc.add("title", "Bulk Test Document " + i + " for ES " + currentESVersion);
      doc.add("content", "Bulk test content " + i);
      doc.add("url", "http://example.com/bulk-test-" + currentESVersion + "-" + i);
      doc.add("tstamp", new Date());
      
      indexWriter.write(doc);
    }
    
    indexWriter.commit();
    
    // Create ES client for verification
    ElasticsearchClient client = createESClient(currentHost, currentPort);
    
    try {
      // Wait for bulk indexing with retry logic
      boolean allDocumentsFound = false;
      for (int attempt = 0; attempt < 15; attempt++) {
        try {
          SearchRequest searchRequest = SearchRequest.of(s -> s
              .index(TEST_INDEX)
              .query(q -> q
                  .wildcard(w -> w
                      .field("id")
                      .value("bulk-doc-" + currentESVersion + "-*")
                  )
              )
              .size(10)
          );
          
          SearchResponse<Object> response = client.search(searchRequest, Object.class);
          
          if (response.hits().total().value() == 5) {
            allDocumentsFound = true;
            break;
          }
          
          LOG.debug("Found {}/5 bulk documents, attempt {}/15", 
                   response.hits().total().value(), attempt + 1);
          Thread.sleep(1000);
        } catch (Exception e) {
          LOG.debug("Bulk search attempt {} failed: {}", attempt + 1, e.getMessage());
          Thread.sleep(1000);
        }
      }
      
      assertTrue("Should find all 5 bulk documents after multiple attempts", allDocumentsFound);
      
    } finally {
      // Close the client properly
      try {
        client._transport().close();
      } catch (IOException e) {
        LOG.warn("Error closing ES client: {}", e.getMessage());
      }
    }
    LOG.info("Bulk operations test passed for ES {}", currentESVersion);
  }

  private void testErrorHandling() throws Exception {
    LOG.info("Testing error handling for ES {}", currentESVersion);
    
    // Test with invalid document (this should be handled gracefully)
    NutchDocument invalidDoc = new NutchDocument();
    // Don't add required fields to test error handling
    
    try {
      indexWriter.write(invalidDoc);
      indexWriter.commit();
      // Should not throw exception, but handle gracefully
      LOG.info("Error handling test passed - invalid document handled gracefully");
    } catch (Exception e) {
      LOG.info("Error handling test passed - exception caught and handled: {}", e.getMessage());
    }
  }

  private ElasticsearchClient createESClient(String host, int port) {
    RestClient restClient = RestClient.builder(
        new HttpHost(host, port, SCHEME)).build();
    
    ElasticsearchTransport transport = new RestClientTransport(
        restClient, new JacksonJsonpMapper());
    
    return new ElasticsearchClient(transport);
  }

  private void waitForElasticsearch(String host, int port) throws Exception {
    System.out.println("Waiting for Elasticsearch at " + host + ":" + port + "...");
    
    HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build();
    
    int maxRetries = 5; // Reduced to 5 attempts
    
    for (int attempt = 1; attempt <= maxRetries; attempt++) {
      System.out.println("Attempt " + attempt + "/" + maxRetries + " to connect to ES");
      try {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://" + host + ":" + port + "/_cluster/health"))
            .timeout(Duration.ofSeconds(2))
            .GET()
            .build();
        
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
          System.out.println("Elasticsearch at " + host + ":" + port + " is ready");
          return;
        }
      } catch (Exception e) {
        System.out.println("Connection attempt " + attempt + " failed: " + e.getClass().getSimpleName());
      }
      
      if (attempt < maxRetries) {
        Thread.sleep(2000); // Wait 2 seconds between attempts
      }
    }
    
    throw new RuntimeException("Elasticsearch at " + host + ":" + port + " did not become ready after " + maxRetries + " attempts");
  }

  private boolean isElasticsearchReachable(String host, int port) {
    try {
      HttpClient client = HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .build();
      
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create("http://" + host + ":" + port + "/"))
          .timeout(Duration.ofSeconds(5))
          .GET()
          .build();
      
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      return response.statusCode() == 200;
    } catch (Exception e) {
      LOG.warn("Elasticsearch not reachable at {}:{}: {}", host, port, e.getMessage());
      return false;
    }
  }

  private void cleanupTestIndex(String host, int port) throws Exception {
    try {
      HttpClient client = HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(3))  // Reduced timeout
          .build();
      
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create("http://" + host + ":" + port + "/" + TEST_INDEX))
          .timeout(Duration.ofSeconds(3))  // Reduced timeout
          .DELETE()
          .build();
      
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      LOG.info("Cleanup response for {}:{}/{}: {}", host, port, TEST_INDEX, response.statusCode());
    } catch (Exception e) {
      LOG.info("Error during cleanup (may be expected if index doesn't exist): {}", e.getMessage());
    }
  }
}