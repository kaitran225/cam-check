# Render.com Optimizations for CamCheck

This document outlines the optimizations made to run CamCheck efficiently on Render.com's free tier environment.

## Environment Characteristics

Based on VM capacity measurements, Render.com's free tier provides:

- **CPU**: 1 processor
- **Memory**: ~512MB total physical memory
- **Heap**: Limited to 38MB max
- **Non-Heap**: ~96MB max
- **Disk**: ~400GB total space
- **OS**: Linux 6.8.0-1032-aws (amd64)
- **Java**: 17.0.15 (Eclipse Adoptium)

## Key Optimizations

### 1. Undertow Web Server

- Reduced worker threads to 2 (from 8+)
- Single I/O thread
- Smaller buffer size (4KB instead of 16KB)
- Disabled direct buffers to reduce off-heap memory usage
- Limited concurrent connections to 50
- Added idle timeout (30s) and request timeout (60s)
- Disabled HTTP/2 to save memory
- Disabled eager filter initialization
- Reduced session timeout to 15 minutes
- Limited backlog to 50 connections
- Reduced header, parameter, and cookie limits

### 2. JVM Optimizations

- Strict heap limits: `-Xmx38m -Xms20m`
- Memory space limits: `-XX:MaxMetaspaceSize=64m -XX:CompressedClassSpaceSize=16m -XX:ReservedCodeCacheSize=16m`
- Serial GC: `-XX:+UseSerialGC` (more efficient for small heaps)
- String deduplication: `-XX:+UseStringDeduplication`
- Disabled explicit GC: `-XX:+DisableExplicitGC`
- Aggressive soft reference clearing: `-XX:SoftRefLRUPolicyMSPerMB=0`
- Limited direct memory: `-XX:MaxDirectMemorySize=5M`
- Compressed pointers: `-XX:+UseCompressedOops -XX:+UseCompressedClassPointers`
- Smaller thread stack: `-XX:ThreadStackSize=256k`

### 3. Memory Management

- Earlier intervention thresholds:
  - High memory: 70% (was 80%)
  - Critical memory: 85% (was 90%)
  - Recovery threshold: 60% (was 70%)
- More frequent memory checks (every 30s instead of 60s)
- Periodic garbage collection (every 2 minutes)
- Aggressive cleanup during first 5 minutes after startup
- Dynamic quality reduction based on memory state
- Default to 80% quality even in normal mode

### 4. Request Throttling

- Limited concurrent requests to 2 (was 5)
- Shorter request timeout (3s instead of 5s)
- CPU-aware throttling that reduces permits when CPU usage is high
- Combined memory and CPU metrics for permit allocation
- More aggressive rejection during high load

### 5. Image Processing

- Smaller image object pool (5 max)
- Fast bilateral filter as default denoise method
- Reduced denoise strength (0.5)
- Performance optimization enabled by default
- Smaller frame cache (5 entries, 5s expiration)
- Limit to 2 concurrent frame processing operations

### 6. VM Capacity Measurement

- Added timeout to prevent startup delays
- Lightweight stress tests to avoid overloading the VM
- Automatic detection of Render.com environment
- Fallback to minimal measurement if full measurement fails
- Dynamic calculation of recommended settings based on measured capacity

### 7. Render.yaml Configuration

- Set `sleepMode: when-idle` to save resources
- Limited disk size to 1GB
- Set health check timeout to 5s
- Set graceful shutdown timeout to 30s
- Configured all environment variables for optimized settings

## Results

These optimizations allow CamCheck to run within Render.com's free tier constraints:
- Memory usage stabilized around 28-30MB heap (well below the 38MB limit)
- Non-heap usage kept under control (~73MB of 96MB max)
- Application remains responsive despite single CPU environment
- Efficient handling of high CPU load through dynamic throttling
- Graceful degradation during high memory usage

## Monitoring

The application exposes detailed metrics through:
- `/api/v1/system/status` endpoint (used for health checks)
- `/api/v1/system/capacity` endpoint (VM capacity information)
- Comprehensive logging of memory, CPU, and resource usage

## Future Improvements

- Consider implementing circuit breakers for non-essential features
- Add adaptive polling intervals based on system load
- Implement request prioritization during high load
- Further optimize image processing for single CPU environment 