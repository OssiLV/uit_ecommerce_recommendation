import os

# --- Bắt buộc dùng Keras 2 cho TFRS ---
os.environ["TF_USE_LEGACY_KERAS"] = "1"
# ------------------------------------------------------

import requests
import numpy as np
import pandas as pd
import tensorflow as tf
import tensorflow_recommenders as tfrs
from io import StringIO

# Cấu hình - Đọc từ environment variable (cho Docker) hoặc dùng default (cho local)
JAVA_API_URL = os.environ.get("JAVA_API_URL", "http://localhost:8080/api/v1/admin/interactions/export")
ADMIN_TOKEN = os.environ.get("ADMIN_TOKEN", "")

# Sử dụng absolute path để đảm bảo model luôn được lưu đúng chỗ
# Trong Docker, /app/saved_models được mount từ host
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_BASE_PATH = os.environ.get("MODEL_BASE_PATH", os.path.join(SCRIPT_DIR, "saved_models"))

# --- ĐỊNH NGHĨA MODEL TENSORFLOW ---

class ECommerceModel(tfrs.Model):
    def __init__(self, user_model, product_model, task):
        super().__init__()
        self.product_model = product_model
        self.user_model = user_model
        self.task = task

    def compute_loss(self, features, training=False):
        user_embeddings = self.user_model(features["user_id"])
        positive_product_embeddings = self.product_model(features["product_id"])
        return self.task(user_embeddings, positive_product_embeddings)


class RecommenderModule(tf.Module):
    """Module để export với TensorFlow Serving"""
    
    def __init__(self, index, k=10):
        super().__init__()
        self.index = index
        self.k = k
    
    @tf.function(input_signature=[tf.TensorSpec(shape=[None], dtype=tf.string)])
    def recommend(self, user_id):
        """Trả về top-k sản phẩm gợi ý cho user"""
        scores, product_ids = self.index(user_id, k=self.k)
        return {
            "scores": scores,
            "product_ids": product_ids
        }


def fetch_data():
    """Lấy dữ liệu từ Java Backend"""
    print("1. Fetching data from Java Backend...")
    
    headers = {}
    if ADMIN_TOKEN:
        headers['Authorization'] = f"Bearer {ADMIN_TOKEN}"
    
    response = requests.get(JAVA_API_URL, headers=headers)
    if response.status_code != 200:
        raise Exception(f"Failed to fetch data from Java. Status: {response.status_code}")

    csv_data = StringIO(response.text)
    df = pd.read_csv(csv_data)
    
    if df.empty:
        raise Exception("Data from Java is empty. Cannot train.")

    df['user_id'] = df['user_id'].astype(str)
    df['product_id'] = df['product_id'].astype(str)
    
    print(f"   Data loaded: {len(df)} rows")
    return df


def build_and_train_model(df):
    """Build và train model"""
    print("2. Building Model...")
    
    # Tạo Dataset
    interactions = tf.data.Dataset.from_tensor_slices({
        "user_id": tf.cast(df['user_id'].values, tf.string),
        "product_id": tf.cast(df['product_id'].values, tf.string)
    })
    
    unique_user_ids = np.unique(df['user_id'].values)
    unique_product_ids = np.unique(df['product_id'].values)
    products = tf.data.Dataset.from_tensor_slices(unique_product_ids)

    embedding_dimension = 32

    # User Model
    user_model = tf.keras.Sequential([
        tf.keras.layers.StringLookup(vocabulary=unique_user_ids, mask_token=None),
        tf.keras.layers.Embedding(len(unique_user_ids) + 1, embedding_dimension)
    ])

    # Product Model
    product_model = tf.keras.Sequential([
        tf.keras.layers.StringLookup(vocabulary=unique_product_ids, mask_token=None),
        tf.keras.layers.Embedding(len(unique_product_ids) + 1, embedding_dimension)
    ])

    # Task
    metrics = tfrs.metrics.FactorizedTopK(candidates=products.batch(128).map(product_model))
    task = tfrs.tasks.Retrieval(metrics=metrics)

    model = ECommerceModel(user_model, product_model, task)
    
    print("3. Training...")
    model.compile(optimizer=tf.keras.optimizers.Adagrad(learning_rate=0.1))
    
    cached_interactions = interactions.batch(128).cache()
    model.fit(cached_interactions, epochs=5)

    print("4. Creating Index...")
    index = tfrs.layers.factorized_top_k.BruteForce(model.user_model, k=10)
    index.index_from_dataset(
        tf.data.Dataset.zip((products.batch(100), products.batch(100).map(model.product_model)))
    )
    
    return model, index, unique_user_ids, unique_product_ids


def save_model_for_serving(index, version=1):
    """Lưu model theo định dạng TensorFlow Serving"""
    print("5. Saving Model for TensorFlow Serving...")
    
    # Wrap index trong RecommenderModule
    module = RecommenderModule(index)
    
    # Tạo đường dẫn với version
    model_path = os.path.join(MODEL_BASE_PATH, "recommender", str(version))
    
    # Lưu với signature
    tf.saved_model.save(
        module, 
        model_path,
        signatures={
            "serving_default": module.recommend.get_concrete_function(
                tf.TensorSpec(shape=[None], dtype=tf.string)
            )
        }
    )
    
    print(f"   Model saved to: {model_path}")
    return model_path


def get_next_version():
    """Lấy version tiếp theo cho model"""
    model_dir = os.path.join(MODEL_BASE_PATH, "recommender")
    if not os.path.exists(model_dir):
        return 1
    
    versions = [int(v) for v in os.listdir(model_dir) if v.isdigit()]
    return max(versions) + 1 if versions else 1


def main():
    """Main function để train và export model"""
    print("=" * 50)
    print("TensorFlow Recommender - Training Script")
    print("=" * 50)
    
    try:
        # Lấy dữ liệu
        df = fetch_data()
        
        # Train model
        model, index, users, products = build_and_train_model(df)
        
        # Lấy version mới
        version = get_next_version()
        
        # Lưu model
        model_path = save_model_for_serving(index, version)
        
        print("=" * 50)
        print("Training completed successfully!")
        print(f"   Users: {len(users)}")
        print(f"   Products: {len(products)}")
        print(f"   Model Version: {version}")
        print(f"   Model Path: {model_path}")
        print("=" * 50)
        print("\nTo start TensorFlow Serving, run:")
        print("   docker-compose up -d")
        print("\nTo get recommendations:")
        print('   curl -X POST http://localhost:8501/v1/models/recommender:predict \\')
        print('        -H "Content-Type: application/json" \\')
        print('        -d \'{"instances": ["user_id_here"]}\'')
        
    except Exception as e:
        print(f"Error: {e}")
        import traceback
        traceback.print_exc()
        return 1
    
    return 0


if __name__ == '__main__':
    exit(main())
