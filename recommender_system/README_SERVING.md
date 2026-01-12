# TensorFlow Serving - Recommender System

## Tổng quan

Hệ thống gợi ý sản phẩm sử dụng TensorFlow Serving để triển khai model.

## Cấu trúc thư mục

```
recommender-system/
├── train_model.py          # Script train và export model
├── docker-compose.yml      # Chạy TensorFlow Serving
├── saved_models/
│   ├── models.config       # Cấu hình TF Serving
│   └── recommender/
│       └── 1/              # Version 1
│           ├── saved_model.pb
│           └── variables/
```

## Hướng dẫn sử dụng

### 1. Train Model

```bash
# Cài đặt dependencies
pip install tensorflow tensorflow-recommenders pandas requests

# Chạy training
python train_model.py
```

### 2. Khởi động TensorFlow Serving

```bash
docker-compose up -d
```

### 3. Gọi API

#### REST API (Port 8501)

**Lấy gợi ý cho 1 user:**
```bash
curl -X POST http://localhost:8501/v1/models/recommender:predict \
  -H "Content-Type: application/json" \
  -d '{"instances": ["user_123"]}'
```

**Lấy gợi ý cho nhiều users:**
```bash
curl -X POST http://localhost:8501/v1/models/recommender:predict \
  -H "Content-Type: application/json" \
  -d '{"instances": ["user_1", "user_2", "user_3"]}'
```

**Response mẫu:**
```json
{
  "predictions": [
    {
      "scores": [0.95, 0.87, 0.82, ...],
      "product_ids": ["prod_1", "prod_2", "prod_3", ...]
    }
  ]
}
```

#### Kiểm tra trạng thái model

```bash
# Model status
curl http://localhost:8501/v1/models/recommender

# Model metadata
curl http://localhost:8501/v1/models/recommender/metadata
```

### 4. Gọi từ Java

```java
// Sử dụng RestTemplate hoặc WebClient
String url = "http://localhost:8501/v1/models/recommender:predict";

Map<String, Object> request = new HashMap<>();
request.put("instances", List.of(userId));

ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
List<Map<String, Object>> predictions = (List) response.getBody().get("predictions");
```

## Retrain Model

Mỗi lần chạy `train_model.py`, model mới sẽ được lưu với version mới. TensorFlow Serving tự động load version mới nhất.

```bash
# Train lại model
python train_model.py

# Không cần restart docker
```

## Cấu hình nâng cao

### Scaling với Kubernetes

TensorFlow Serving hỗ trợ triển khai trên Kubernetes với auto-scaling dựa trên CPU/Memory usage.

### Batching

Để tăng throughput, bật batching trong model config:

```
batching_parameters {
  max_batch_size { value: 128 }
  batch_timeout_micros { value: 1000 }
  num_batch_threads { value: 8 }
}
```
