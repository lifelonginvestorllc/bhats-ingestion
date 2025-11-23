# Quick Reference Guide - Package Structure

## Package Organization

### 📦 common (3 files) - Shared Models & Interfaces
```
src/main/java/com/example/payload/common/
├── TSValues.java                    # Data model for time-series values
├── PayloadStatus.java               # Status model (shared between packages)
└── StatusPublisher.java             # Interface for decoupling
```

**Purpose:** Provides shared contracts and models that both bhpubwrt and bhwrtam depend on.

---

### 📦 bhpubwrt (5 files) - Producer & Reply Aggregation
```
src/main/java/com/example/payload/bhpubwrt/
├── BhpubwrtProducer.java            # Publishes payloads; implements StatusPublisher
├── KafkaStatusConsumer.java         # Listens to 3 consumer groups for replies
├── ClusterStatusAggregator.java     # Aggregates status from multiple clusters
├── AggregatedPayloadStatus.java    # Consolidated multi-cluster status
└── StatusStore.java                 # Thread-safe status storage
```

**Purpose:** Handles payload publishing and aggregation of status replies from 3 simulated clusters.

**Key Responsibilities:**
- ✅ Publish payloads to Kafka
- ✅ Listen for status replies from 3 consumer groups
- ✅ Aggregate multi-cluster status
- ✅ Implement StatusPublisher interface

---

### 📦 bhwrtam (5 files) - Consumer & Batch Processor
```
src/main/java/com/example/payload/bhwrtam/
├── BhwrtamConsumer.java             # Kafka listener for incoming payloads
├── KafkaPayloadProcessor.java       # Core processing with blocking queues
├── StatusTracker.java               # Tracks sub-batch completion
├── SubBatch.java                    # Batch of records grouped by key
└── SubBatchStatus.java              # Enum: SUCCESS/FAILURE
```

**Purpose:** Consumes payloads, processes them in batches using worker queues, and publishes completion status.

**Key Responsibilities:**
- ✅ Consume payloads from Kafka
- ✅ Split into sub-batches grouped by key
- ✅ Route to 4 worker queues
- ✅ Process batches with configurable failure simulation
- ✅ Publish status via StatusPublisher interface (no direct dependency on bhpubwrt)

---

## Decoupling Strategy

### Before (Tightly Coupled)
```
bhwrtam/KafkaPayloadProcessor
    └── depends on ──► bhpubwrt/BhpubwrtProducer
```
❌ Direct dependency creates coupling

### After (Loosely Coupled)
```
common/StatusPublisher (interface)
    ▲                          ▲
    │                          │
    │ implements               │ depends on
    │                          │
bhpubwrt/BhpubwrtProducer    bhwrtam/KafkaPayloadProcessor
```
✅ Both packages only depend on common interface

---

## Key Files for Decoupling

### 1. StatusPublisher Interface (common)
```java
public interface StatusPublisher {
    void publishStatus(PayloadStatus status);
}
```
- Defines the contract
- Lives in common package
- Used by bhwrtam without knowing bhpubwrt

### 2. PayloadStatus Model (common)
```java
public class PayloadStatus {
    public String payloadId;
    public boolean success;
    public int batchCount;
    public long completedAt;
    public String clusterId;
}
```
- Shared data model
- Used by both packages
- Single source of truth

### 3. BhpubwrtProducer (bhpubwrt)
```java
@Component
public class BhpubwrtProducer implements StatusPublisher {
    @Override
    public void publishStatus(PayloadStatus status) {
        statusKafkaTemplate.send(REPLY_TOPIC, status.payloadId, status);
    }
}
```
- Implements the interface
- Provides concrete implementation
- Can be replaced with different implementation

### 4. KafkaPayloadProcessor (bhwrtam)
```java
@Service
public class KafkaPayloadProcessor {
    @Autowired(required = false)
    private StatusPublisher statusPublisher;  // Interface, not concrete class!
    
    private void handleCompletePayload(String payloadId) {
        if (statusPublisher != null) {
            statusPublisher.publishStatus(new PayloadStatus(...));
        }
    }
}
```
- Depends only on StatusPublisher interface
- No knowledge of BhpubwrtProducer
- Spring injects implementation at runtime

---

## Dependency Graph

```
┌─────────────┐     ┌─────────────┐
│  bhpubwrt   │     │  bhwrtam    │
│  (5 files)  │     │  (5 files)  │
└──────┬──────┘     └──────┬──────┘
       │                   │
       │ depends on        │ depends on
       │                   │
       └───────┬───────────┘
               ▼
        ┌─────────────┐
        │   common    │
        │  (3 files)  │
        │             │
        │ • TSValues  │
        │ • Status    │
        │ • Interface │
        └─────────────┘
```

**No circular dependencies!** ✅

---

## How to Deploy as Separate Services

### Step 1: Create Maven Modules
```xml
<modules>
    <module>bhats-common</module>      <!-- common package -->
    <module>bhats-producer</module>    <!-- bhpubwrt package -->
    <module>bhats-consumer</module>    <!-- bhwrtam package -->
</modules>
```

### Step 2: Define Dependencies
```xml
<!-- bhats-producer/pom.xml -->
<dependency>
    <groupId>com.example</groupId>
    <artifactId>bhats-common</artifactId>
</dependency>

<!-- bhats-consumer/pom.xml -->
<dependency>
    <groupId>com.example</groupId>
    <artifactId>bhats-common</artifactId>
</dependency>
```

### Step 3: Deploy Independently
```bash
# Producer service
java -jar bhats-producer.jar --server.port=8081

# Consumer service (cluster 1)
java -jar bhats-consumer.jar --server.port=8082

# Consumer service (cluster 2)
java -jar bhats-consumer.jar --server.port=8083

# Consumer service (cluster 3)
java -jar bhats-consumer.jar --server.port=8084
```

---

## Testing

All tests pass successfully:
```bash
mvn test
```

**Test Coverage:**
- ✅ KafkaIntegrationTest
- ✅ KafkaFailureIntegrationTest  
- ✅ MultiClusterKafkaIntegrationTest
- ✅ KafkaPayloadProcessorTest
- ✅ KafkaPayloadProcessorShutdownTest

**Results:** 5 tests, 0 failures, 0 errors

---

## Benefits Summary

| Benefit | Description |
|---------|-------------|
| **Independence** | Each package can be developed/deployed separately |
| **Scalability** | Scale producer and consumers independently |
| **Testability** | Easy to mock interfaces for testing |
| **Maintainability** | Changes in one package don't affect the other |
| **Flexibility** | Easy to swap implementations |
| **Clear Contracts** | Interface defines clear API boundaries |

---

## Quick Commands

### Compile
```bash
mvn clean compile
```

### Run Tests
```bash
mvn test
```

### Package
```bash
mvn package
```

### Run Application
```bash
java -jar target/bhats-ingestion-1.0-SNAPSHOT.jar
```

---

## References

- **PACKAGE_DECOUPLING.md** - Detailed decoupling documentation
- **ARCHITECTURE_DIAGRAM.md** - Visual architecture diagrams
- **pom.xml** - Maven configuration
- **src/main/resources/application.properties** - Application configuration

