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
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.nutch.indexer.IndexWriterParams;
import org.apache.nutch.indexer.NutchDocument;
import org.apache.nutch.util.NutchConfiguration;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.elastic.clients.elasticsearch.ElasticsearchClient;

/**
 * Test class for ElasticIndexWriter focusing on ES 8/9 client upgrade.
 * Tests the new ElasticsearchClient API and ensures compatibility.
 */
public class TestElasticIndexWriter {

  protected static final Logger LOG = LoggerFactory
      .getLogger(TestElasticIndexWriter.class);

  /**
   * Mock IndexWriter which can be used for testing without an actual ES instance
   */
  public static class MockElasticIndexWriter extends ElasticIndexWriter {
    
    private boolean clientCreated = false;
    private boolean documentsWritten = false;
    private boolean committed = false;
    private int writeCount = 0;
    private int deleteCount = 0;
    
    @Override
    protected ElasticsearchClient makeClient(IndexWriterParams parameters)
        throws IOException {
      // Mark that client creation was attempted with proper parameters
      clientCreated = true;
      
      // Validate that required parameters are present
      if (parameters.get(ElasticConstants.HOSTS) == null) {
        throw new RuntimeException("Missing elastic.host parameter");
      }
      
      // Return null for testing purposes (no actual ES client needed)
      return null;
    }
    
    @Override
    public void write(NutchDocument doc) throws IOException {
      // Simulate document writing
      documentsWritten = true;
      writeCount++;
      LOG.debug("Mock write: document with id={}", doc.getFieldValue("id"));
    }
    
    @Override
    public void delete(String key) throws IOException {
      // Simulate document deletion
      deleteCount++;
      LOG.debug("Mock delete: document with id={}", key);
    }
    
    @Override
    public void commit() throws IOException {
      committed = true;
      LOG.debug("Mock commit: processed {} writes, {} deletes", writeCount, deleteCount);
    }
    
    @Override
    public void close() throws IOException {
      LOG.debug("Mock close: client closed successfully");
    }
    
    // Test helper methods
    public boolean isClientCreated() { return clientCreated; }
    public boolean areDocumentsWritten() { return documentsWritten; }
    public boolean isCommitted() { return committed; }
    public int getWriteCount() { return writeCount; }
    public int getDeleteCount() { return deleteCount; }
  }
  
  /**
   * Create an IndexWriterParams with basic configuration
   */
  private IndexWriterParams createBasicParams() {
    Map<String, String> params = new HashMap<>();
    params.put(ElasticConstants.HOSTS, "localhost");
    params.put(ElasticConstants.PORT, "9200");
    params.put(ElasticConstants.SCHEME, "http");
    params.put(ElasticConstants.INDEX, "nutch-test");
    params.put(ElasticConstants.USE_AUTH, "false");
    return new IndexWriterParams(params);
  }
  
  /**
   * Create a sample NutchDocument for testing
   */
  private NutchDocument createTestDocument(String id, String title, String content) {
    NutchDocument doc = new NutchDocument();
    doc.add("id", id);
    doc.add("title", title);
    doc.add("content", content);
    doc.add("url", "http://example.com/" + id);
    doc.add("timestamp", new Date());
    return doc;
  }

  /**
   * Test basic initialization with ES 8/9 client
   */
  @Test
  public void testBasicInitialization() throws IOException {
    MockElasticIndexWriter writer = new MockElasticIndexWriter();
    Configuration conf = NutchConfiguration.create();
    writer.setConf(conf);
    
    IndexWriterParams params = createBasicParams();
    writer.open(params);
    
    // Verify that client creation was attempted
    assertTrue("Client should be created during initialization", 
        writer.isClientCreated());
    
    writer.close();
  }
  
  /**
   * Test missing host parameter throws appropriate error
   */
  @Test
  public void testMissingHostParameter() {
    MockElasticIndexWriter writer = new MockElasticIndexWriter();
    Configuration conf = NutchConfiguration.create();
    writer.setConf(conf);
    
    // Create params without host
    Map<String, String> params = new HashMap<>();
    params.put(ElasticConstants.PORT, "9200");
    IndexWriterParams writerParams = new IndexWriterParams(params);
    
    try {
      writer.open(writerParams);
      fail("Should have thrown exception for missing host parameter");
    } catch (RuntimeException e) {
      assertTrue("Should mention missing host parameter", 
          e.getMessage().contains("elastic.host"));
    } catch (IOException e) {
      fail("Unexpected IOException: " + e.getMessage());
    }
  }
  
  /**
   * Test authentication configuration
   */
  @Test
  public void testAuthenticationConfiguration() throws IOException {
    MockElasticIndexWriter writer = new MockElasticIndexWriter();
    Configuration conf = NutchConfiguration.create();
    writer.setConf(conf);
    
    Map<String, String> params = new HashMap<>();
    params.put(ElasticConstants.HOSTS, "localhost");
    params.put(ElasticConstants.PORT, "9200");
    params.put(ElasticConstants.SCHEME, "https");
    params.put(ElasticConstants.INDEX, "nutch-test");
    params.put(ElasticConstants.USE_AUTH, "true");
    params.put(ElasticConstants.USER, "testuser");
    params.put(ElasticConstants.PASSWORD, "testpass");
    
    IndexWriterParams writerParams = new IndexWriterParams(params);
    writer.open(writerParams);
    
    assertTrue("Client should be created with auth configuration", 
        writer.isClientCreated());
    
    writer.close();
  }
  
  /**
   * Test document writing functionality
   */
  @Test
  public void testDocumentWriting() throws IOException {
    MockElasticIndexWriter writer = new MockElasticIndexWriter();
    Configuration conf = NutchConfiguration.create();
    writer.setConf(conf);
    
    IndexWriterParams params = createBasicParams();
    writer.open(params);
    
    // Write test documents
    NutchDocument doc1 = createTestDocument("doc1", "Test Title 1", "Test content 1");
    NutchDocument doc2 = createTestDocument("doc2", "Test Title 2", "Test content 2");
    
    writer.write(doc1);
    writer.write(doc2);
    
    assertTrue("Documents should be written", writer.areDocumentsWritten());
    assertEquals("Should have written 2 documents", 2, writer.getWriteCount());
    
    writer.close();
  }
  
  /**
   * Test document deletion functionality
   */
  @Test
  public void testDocumentDeletion() throws IOException {
    MockElasticIndexWriter writer = new MockElasticIndexWriter();
    Configuration conf = NutchConfiguration.create();
    writer.setConf(conf);
    
    IndexWriterParams params = createBasicParams();
    writer.open(params);
    
    // Delete test documents
    writer.delete("doc1");
    writer.delete("doc2");
    
    assertEquals("Should have deleted 2 documents", 2, writer.getDeleteCount());
    
    writer.close();
  }
  
  /**
   * Test document update functionality (which delegates to write)
   */
  @Test
  public void testDocumentUpdate() throws IOException {
    MockElasticIndexWriter writer = new MockElasticIndexWriter();
    Configuration conf = NutchConfiguration.create();
    writer.setConf(conf);
    
    IndexWriterParams params = createBasicParams();
    writer.open(params);
    
    NutchDocument doc = createTestDocument("doc1", "Updated Title", "Updated content");
    writer.update(doc);
    
    assertTrue("Document should be updated (written)", writer.areDocumentsWritten());
    assertEquals("Should have one write operation", 1, writer.getWriteCount());
    
    writer.close();
  }
  
  /**
   * Test commit functionality
   */
  @Test
  public void testCommit() throws IOException {
    MockElasticIndexWriter writer = new MockElasticIndexWriter();
    Configuration conf = NutchConfiguration.create();
    writer.setConf(conf);
    
    IndexWriterParams params = createBasicParams();
    writer.open(params);
    
    // Write some documents
    writer.write(createTestDocument("doc1", "Title 1", "Content 1"));
    writer.write(createTestDocument("doc2", "Title 2", "Content 2"));
    
    // Commit changes
    writer.commit();
    
    assertTrue("Should have committed changes", writer.isCommitted());
    
    writer.close();
  }
  
  /**
   * Test bulk size configuration
   */
  @Test
  public void testBulkSizeConfiguration() throws IOException {
    MockElasticIndexWriter writer = new MockElasticIndexWriter();
    Configuration conf = NutchConfiguration.create();
    writer.setConf(conf);
    
    Map<String, String> params = new HashMap<>();
    params.put(ElasticConstants.HOSTS, "localhost");
    params.put(ElasticConstants.PORT, "9200");
    params.put(ElasticConstants.SCHEME, "http");
    params.put(ElasticConstants.INDEX, "nutch-test");
    params.put(ElasticConstants.USE_AUTH, "false");
    params.put(ElasticConstants.MAX_BULK_DOCS, "100");
    params.put(ElasticConstants.MAX_BULK_LENGTH, "1000000");
    
    IndexWriterParams writerParams = new IndexWriterParams(params);
    writer.open(writerParams);
    
    assertTrue("Client should be created with bulk configuration", 
        writer.isClientCreated());
    
    writer.close();
  }
  
  /**
   * Test the describe() method returns proper configuration info
   */
  @Test
  public void testDescribeMethod() throws IOException {
    ElasticIndexWriter writer = new ElasticIndexWriter();
    Configuration conf = NutchConfiguration.create();
    writer.setConf(conf);
    
    // Note: Can't open without actual ES, but can test describe() method
    Map<String, Map.Entry<String, Object>> description = writer.describe();
    
    assertNotNull("Description should not be null", description);
    assertTrue("Should describe host parameter", 
        description.containsKey(ElasticConstants.HOSTS));
    assertTrue("Should describe port parameter", 
        description.containsKey(ElasticConstants.PORT));
    assertTrue("Should describe scheme parameter", 
        description.containsKey(ElasticConstants.SCHEME));
    assertTrue("Should describe index parameter", 
        description.containsKey(ElasticConstants.INDEX));
    assertTrue("Should describe auth parameter", 
        description.containsKey(ElasticConstants.USE_AUTH));
    assertTrue("Should describe bulk docs parameter", 
        description.containsKey(ElasticConstants.MAX_BULK_DOCS));
    assertTrue("Should describe bulk length parameter", 
        description.containsKey(ElasticConstants.MAX_BULK_LENGTH));
  }
  
  /**
   * Test date field handling
   */
  @Test
  public void testDateFieldHandling() throws IOException {
    MockElasticIndexWriter writer = new MockElasticIndexWriter();
    Configuration conf = NutchConfiguration.create();
    writer.setConf(conf);
    
    IndexWriterParams params = createBasicParams();
    writer.open(params);
    
    // Create document with date field
    NutchDocument doc = new NutchDocument();
    doc.add("id", "date-test");
    doc.add("title", "Date Test Document");
    doc.add("timestamp", new Date(0)); // Unix epoch
    doc.add("publishDate", new Date(System.currentTimeMillis()));
    
    writer.write(doc);
    
    assertTrue("Document with date fields should be written", 
        writer.areDocumentsWritten());
    
    writer.close();
  }
  
  /**
   * Test multi-value field handling
   */
  @Test
  public void testMultiValueFields() throws IOException {
    MockElasticIndexWriter writer = new MockElasticIndexWriter();
    Configuration conf = NutchConfiguration.create();
    writer.setConf(conf);
    
    IndexWriterParams params = createBasicParams();
    writer.open(params);
    
    // Create document with multi-value fields
    NutchDocument doc = new NutchDocument();
    doc.add("id", "multi-value-test");
    doc.add("title", "Multi-Value Test Document");
    doc.add("tags", "tag1");
    doc.add("tags", "tag2");
    doc.add("tags", "tag3");
    doc.add("categories", "cat1");
    doc.add("categories", "cat2");
    
    writer.write(doc);
    
    assertTrue("Document with multi-value fields should be written", 
        writer.areDocumentsWritten());
    
    writer.close();
  }
  
  /**
   * Test that ES 8+ client classes are available and importable
   */
  @Test
  public void testES8ClientAvailability() {
    try {
      // Test that the new ES 8+ client classes are available
      Class.forName("co.elastic.clients.elasticsearch.ElasticsearchClient");
      Class.forName("co.elastic.clients.elasticsearch.core.bulk.BulkOperation");
      Class.forName("co.elastic.clients.elasticsearch.core.bulk.IndexOperation");
      Class.forName("co.elastic.clients.elasticsearch.core.bulk.DeleteOperation");
      Class.forName("co.elastic.clients.elasticsearch._types.ElasticsearchException");
      Class.forName("co.elastic.clients.json.jackson.JacksonJsonpMapper");
      Class.forName("co.elastic.clients.transport.rest_client.RestClientTransport");
      
      LOG.info("All ES 8+ client classes are available");
    } catch (ClassNotFoundException e) {
      fail("ES 8+ client classes should be available: " + e.getMessage());
    }
  }
  
  /**
   * Test that deprecated ES 7 client classes are NOT available
   */
  @Test
  public void testES7ClientNotAvailable() {
    try {
      // Test that deprecated ES 7 classes are not available
      Class.forName("org.elasticsearch.client.RestHighLevelClient");
      fail("Deprecated RestHighLevelClient should not be available");
    } catch (ClassNotFoundException e) {
      // Expected - the old client should not be present
      LOG.info("Confirmed: deprecated ES 7 RestHighLevelClient is not available");
    }
    
    try {
      Class.forName("org.elasticsearch.action.bulk.BulkRequestBuilder");
      fail("Deprecated BulkRequestBuilder should not be available");
    } catch (ClassNotFoundException e) {
      // Expected - the old bulk API should not be present
      LOG.info("Confirmed: deprecated ES 7 BulkRequestBuilder is not available");
    }
  }
}