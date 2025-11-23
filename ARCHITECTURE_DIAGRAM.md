# Package Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         KAFKA INFRASTRUCTURE                             │
│                                                                          │
│  Topic: payload-topic          Topic: payload-status                    │
│  ┌────────────────┐            ┌────────────────┐                      │
│  │   Payloads     │            │    Statuses    │                      │
│  └────────────────┘            └────────────────┘                      │
└─────────────────────────────────────────────────────────────────────────┘
           ▲                                │
           │ publishes                      │ consumes (3 consumer groups)
           │                                ▼
┌──────────┴──────────────────┐  ┌─────────────────────────────────┐
│   BHPUBWRT PACKAGE          │  │                                  │
│   (Producer/Aggregator)     │  │   KAFKA STATUS CONSUMER          │
│                             │  │   (3 listeners)                  │
│  ┌────────────────────┐    │  │                                  │
│  │ BhpubwrtProducer   │────┼──┼──► cluster-1 listener            │
│  │ (implements        │    │  │  ► cluster-2 listener            │
│  │  StatusPublisher)  │    │  │  ► cluster-3 listener            │
│  └────────────────────┘    │  │                                  │
│           │                 │  └──────────────┬──────────────────┘
│           │                 │                 │
│           ▼                 │                 ▼
│  ┌────────────────────┐    │  ┌─────────────────────────────────┐
│  │ClusterStatus       │    │  │  StatusStore                     │
│  │Aggregator          │◄───┼──┤  (thread-safe status storage)   │
│  └────────────────────┘    │  └─────────────────────────────────┘
│           │                 │
│           ▼                 │
│  ┌────────────────────┐    │
│  │AggregatedPayload   │    │
│  │Status              │    │
│  │ • allSuccessful    │    │
│  │ • atLeastOneSuccess│    │
│  │ • clusterIds       │    │
│  └────────────────────┘    │
└─────────────────────────────┘
           ▲
           │ implements interface from common
           │
┌──────────┴──────────────────────────────────────────────────────────────┐
│                         COMMON PACKAGE                                   │
│                      (Shared Models & Interfaces)                        │
│                                                                          │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐     │
│  │  TSValues        │  │ PayloadStatus    │  │ StatusPublisher  │     │
│  │  (data model)    │  │  • payloadId     │  │  <<interface>>   │     │
│  │                  │  │  • success       │  │                  │     │
│  │  • key           │  │  • batchCount    │  │ +publishStatus() │     │
│  │  • value         │  │  • clusterId     │  └──────────────────┘     │
│  └──────────────────┘  └──────────────────┘                            │
└──────────────────────────────────────────────────────────────────────────┘
           │
           │ uses interface
           ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      BHWRTAM PACKAGE                                     │
│                  (Consumer/Processor)                                    │
│                                                                          │
│  ┌────────────────────┐         ┌─────────────────────────────────┐   │
│  │ BhwrtamConsumer    │────────►│ KafkaPayloadProcessor            │   │
│  │ (@KafkaListener)   │         │                                  │   │
│  └────────────────────┘         │ • Autowires StatusPublisher      │   │
│           │                      │   (doesn't know BhpubwrtProducer)│   │
│           │ submits              │                                  │   │
│           ▼                      │ ┌─────────────────────────┐     │   │
│  ┌────────────────────┐         │ │  4 Blocking Queues      │     │   │
│  │  Large Payload     │         │ │  (route by key hash)    │     │   │
│  │  (TSValues[])      │────────►│ │                         │     │   │
│  └────────────────────┘         │ │  Queue 0 ◄── Worker 0   │     │   │
│                                  │ │  Queue 1 ◄── Worker 1   │     │   │
│                                  │ │  Queue 2 ◄── Worker 2   │     │   │
│           splits into            │ │  Queue 3 ◄── Worker 3   │     │   │
│                │                 │ └─────────────────────────┘     │   │
│                ▼                 │           │                      │   │
│  ┌───────────────────────┐      │           ▼                      │   │
│  │  SubBatch             │      │  ┌─────────────────────────┐    │   │
│  │  • payloadId          │      │  │  StatusTracker          │    │   │
│  │  • index              │      │  │  (tracks sub-batches)   │    │   │
│  │  • key                │      │  └─────────────────────────┘    │   │
│  │  • records[]          │      │           │                      │   │
│  └───────────────────────┘      │           ▼                      │   │
│                                  │  ┌─────────────────────────┐    │   │
│                                  │  │  Payload Complete       │    │   │
│                                  │  │  (via Flux/Reactor)     │    │   │
│                                  │  └─────────────────────────┘    │   │
│                                  │           │                      │   │
│                                  │           ▼                      │   │
│                                  │  ┌─────────────────────────┐    │   │
│                                  │  │ statusPublisher         │    │   │
│                                  │  │ .publishStatus(...)     │    │   │
│                                  └──┴─────────────────────────┴────┘   │
└─────────────────────────────────────────────────────────────────────────┘
                                           │
                                           │ publishes status
                                           ▼
                               ┌────────────────────────┐
                               │ payload-status topic   │
                               └────────────────────────┘
                                           │
                                           │ consumed by 3 groups
                                           ▼
                                    (back to bhpubwrt)


DEPENDENCY FLOW:
═════════════════

┌─────────────┐
│  bhpubwrt   │───┐
└─────────────┘   │
                  │
                  ├───► ┌─────────────┐
┌─────────────┐   │     │   common    │
│  bhwrtam    │───┘     │ (interface) │
└─────────────┘         └─────────────┘

✓ No circular dependencies
✓ Both packages depend only on common
✓ bhpubwrt and bhwrtam are independent


KEY DECOUPLING POINTS:
════════════════════════

1. StatusPublisher Interface (common)
   - Defines the contract
   - bhwrtam depends on interface
   - bhpubwrt implements interface

2. PayloadStatus Model (common)
   - Shared data structure
   - Used by both packages
   - Single source of truth

3. Kafka Topics (infrastructure)
   - Only communication channel
   - Asynchronous messaging
   - Event-driven architecture

4. Spring Dependency Injection
   - Runtime wiring
   - Compile-time separation
   - Optional injection (@Autowired(required = false))
```

