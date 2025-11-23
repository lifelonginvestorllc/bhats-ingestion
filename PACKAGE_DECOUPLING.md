# Package Decoupling Summary

## Overview
The codebase has been successfully reorganized to create explicit separation between the `bhpubwrt` (producer/aggregator) and `bhwrtam` (consumer/processor) packages. This decoupling allows each package to be developed and deployed as separate microservices in the future.

## Package Structure

### 1. **common** Package (Shared Models)
**Location:** `com.example.payload.common`

**Purpose:** Contains shared models and interfaces used by both bhpubwrt and bhwrtam packages.

**Files:**
- `TSValues.java` - Data model representing time-series values
- `PayloadStatus.java` - Completion status of a payload from a single cluster/consumer group
- `StatusPublisher.java` - Interface for publishing payload completion status (decoupling mechanism)

**Key Design:**
- `StatusPublisher` interface provides the abstraction layer
- Both packages can depend on common without creating circular dependencies
- Shared models ensure consistency across services

### 2. **bhpubwrt** Package (Producer & Reply Aggregation)
**Location:** `com.example.payload.bhpubwrt`

**Purpose:** Handles publishing payloads to Kafka topic and aggregating status replies from multiple clusters.

**Components:**
- `BhpubwrtProducer.java` - Publishes payloads to Kafka; implements `StatusPublisher` interface
- `KafkaStatusConsumer.java` - Consumes status replies from 3 different consumer groups (simulating 3 clusters)
- `PayloadStatus.java` - **(MOVED to common package)**
- `ClusterStatusAggregator.java` - Aggregates status from multiple clusters
- `AggregatedPayloadStatus.java` - Consolidated status from all clusters
- `StatusStore.java` - Thread-safe storage for payload statuses

**Responsibilities:**
- Publish payloads to `payload-topic`
- Listen to `payload-status` topic with 3 consumer groups (cluster-1, cluster-2, cluster-3)
- Aggregate replies from all 3 clusters
- Determine overall success (all successful, partial failure, complete failure)
- Track which clusters have responded

**Dependencies:**
- Depends on: `common` package only
- No dependency on `bhwrtam`

### 3. **bhwrtam** Package (Payload Ingestion & Batch Processing)
**Location:** `com.example.payload.bhwrtam`

**Purpose:** Consumes payloads from Kafka, splits them into batches, processes with blocking queues, and publishes completion status.

**Components:**
- `BhwrtamConsumer.java` - Kafka listener consuming from `payload-topic`
- `KafkaPayloadProcessor.java` - Core processing logic with blocking queues and worker threads
- `StatusTracker.java` - Tracks sub-batch completion status
- `SubBatch.java` - Represents a batch of records grouped by key
- `SubBatchStatus.java` - Enum for SUCCESS/FAILURE

**Responsibilities:**
- Consume payloads from `payload-topic`
- Split large payloads into sub-batches grouped by key
- Route sub-batches to 4 worker queues
- Process batches with configurable failure simulation
- Aggregate batch results and determine payload success
- Publish completion status via `StatusPublisher` interface

**Dependencies:**
- Depends on: `common` package (specifically `StatusPublisher` interface)
- **No direct dependency on `bhpubwrt`**

**Decoupling Mechanism:**
```java
@Autowired(required = false)
private StatusPublisher statusPublisher;
```
- Uses Spring's dependency injection to get `StatusPublisher` implementation
- `BhpubwrtProducer` implements this interface
- `bhwrtam` package doesn't know about `BhpubwrtProducer` class

## Decoupling Benefits

### 1. **Independent Deployment**
- Each package can be packaged as a separate JAR/WAR
- Can be deployed as separate microservices
- Scale independently based on load

### 2. **Clear Responsibilities**
- **bhpubwrt:** Publishing + aggregation + status management
- **bhwrtam:** Consumption + batch processing + per-cluster ingestion
- **common:** Shared contracts and models

### 3. **Technology Flexibility**
- `bhwrtam` could be replaced with a different implementation (e.g., using different message queue)
- `bhpubwrt` could publish to multiple messaging systems
- Interface-based design allows for mock implementations in tests

### 4. **Reduced Coupling**
- No circular dependencies
- Changes in `bhwrtam` don't affect `bhpubwrt` (and vice versa)
- Common models are stable contracts

## How Multi-Cluster Works

### Data Flow:
1. **Publishing Phase (bhpubwrt):**
   - `BhpubwrtProducer` publishes payload to `payload-topic`
   - Initializes `ClusterStatusAggregator` expecting 3 replies

2. **Processing Phase (bhwrtam):**
   - 3 separate `BhwrtamConsumer` instances (representing 3 clusters) consume the same payload
   - Each instance processes independently with its own `KafkaPayloadProcessor`
   - Each completes and publishes status to `payload-status` topic via `StatusPublisher`

3. **Aggregation Phase (bhpubwrt):**
   - `KafkaStatusConsumer` has 3 listeners (cluster-1, cluster-2, cluster-3)
   - Each listener annotates the status with `clusterId`
   - `ClusterStatusAggregator` collects all 3 replies
   - `AggregatedPayloadStatus` provides overall result:
     - `allClustersReported`: All 3 replied
     - `allSuccessful`: All 3 succeeded
     - `atLeastOneSuccess`: At least 1 succeeded (partial success)

## Future Separation Steps

### To deploy as separate services:

1. **Create separate Maven modules:**
   ```
   bhats-ingestion-parent/
   ├── bhats-common/        (common package)
   ├── bhats-producer/      (bhpubwrt package)
   └── bhats-consumer/      (bhwrtam package)
   ```

2. **Separate configuration:**
   - Each service gets its own `application.properties`
   - Configure Kafka bootstrap servers per service
   - Producer only needs producer configs
   - Consumer only needs consumer configs

3. **API/Contract Definition:**
   - `common` module defines the API contract
   - Both services depend on common
   - Common module published as a shared library

4. **Service Communication:**
   - Services communicate only via Kafka topics
   - No direct HTTP/RPC calls needed
   - Event-driven architecture

## Test Coverage

All tests pass successfully:
- `KafkaIntegrationTest` - Basic payload processing
- `KafkaFailureIntegrationTest` - Failure scenarios
- `MultiClusterKafkaIntegrationTest` - Multi-cluster aggregation
- `KafkaPayloadProcessorTest` - Unit tests for processor
- `KafkaPayloadProcessorShutdownTest` - Graceful shutdown

## Key Implementation Details

### StatusPublisher Interface:
```java
public interface StatusPublisher {
    void publishStatus(PayloadStatus status);
}
```

### Implementation (bhpubwrt):
```java
@Component
public class BhpubwrtProducer implements StatusPublisher {
    @Override
    public void publishStatus(PayloadStatus status) {
        statusKafkaTemplate.send(REPLY_TOPIC, status.payloadId, status);
    }
}
```

### Usage (bhwrtam):
```java
@Service
public class KafkaPayloadProcessor {
    @Autowired(required = false)
    private StatusPublisher statusPublisher;
    
    private void handleCompletePayload(String payloadId) {
        if (statusPublisher != null) {
            statusPublisher.publishStatus(new PayloadStatus(...));
        }
    }
}
```

This design enables clean separation while maintaining functionality through well-defined interfaces.

