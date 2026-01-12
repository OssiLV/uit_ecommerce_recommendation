# Dockerfile for Model Trainer
# Sử dụng Python để train model và lưu vào thư mục saved_models

FROM python:3.10-slim

# Set working directory
WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements first for better caching
COPY requirements.txt .

# Install Python dependencies
RUN pip install --no-cache-dir -r requirements.txt

# Copy training script
COPY train_model.py .
COPY saved_models/models.config saved_models/

# Set environment variables
ENV TF_USE_LEGACY_KERAS=1
ENV PYTHONUNBUFFERED=1

# Default API URL (sẽ được override bởi docker-compose)
ENV JAVA_API_URL=http://ecommerce-backend:8080/api/v1/admin/interactions/export

# Run training script
CMD ["python", "train_model.py"]
