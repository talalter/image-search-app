import boto3  # type: ignore
from dotenv import load_dotenv # type: ignore
import os

BUCKET_NAME = "images-search-app"
load_dotenv()

storage_backend = os.getenv("STORAGE_BACKEND", "local").lower()

def upload_folder_to_bucket(file, key, upload_type='folder'):
    """Uploads a folder or file to an S3 bucket."""
    s3 = boto3.client(
        's3',
        aws_access_key_id=os.getenv("AWS_ACCESS_KEY_ID"),
        aws_secret_access_key=os.getenv("AWS_SECRET_ACCESS_KEY"),
        region_name=os.getenv("AWS_REGION")
        )
    if upload_type == 'folder':
        file.file.seek(0)
        s3.upload_fileobj(
            file.file,
            BUCKET_NAME,
            key,
            ExtraArgs={"ContentType": file.content_type}
        )
    elif upload_type == 'faiss':
        s3.upload_file(file, BUCKET_NAME, key)


def upload_folder_to_local(file, key, upload_type='folder'):
    # Get project root directory (one level up from python-backend)
    current_dir = os.getcwd()
    # If we're in python-backend, go up one level
    if os.path.basename(current_dir) == 'python-backend':
        project_root = os.path.dirname(current_dir)
    else:
        project_root = current_dir

    # Build path: {project-root}/data/uploads/{key}
    folder_path = os.path.join(project_root, 'data', 'uploads', key)

    os.makedirs(os.path.dirname(folder_path), exist_ok=True)

    if upload_type == 'folder':
        file.file.seek(0)
        with open(folder_path, 'wb') as f:
            f.write(file.file.read())

    return folder_path

def generate_presigned_url(s3_key: str, bucket_name=BUCKET_NAME, expiration=60) -> str:
    s3 = boto3.client(
        's3',
        aws_access_key_id=os.getenv("AWS_ACCESS_KEY_ID"),
        aws_secret_access_key=os.getenv("AWS_SECRET_ACCESS_KEY"),
        region_name=os.getenv("AWS_REGION")
        )
    """Generate a presigned URL for an object in S3."""
    return s3.generate_presigned_url(
        ClientMethod='get_object',
        Params={'Bucket': bucket_name, 'Key': s3_key},
        ExpiresIn=expiration
    )


def get_path_to_save(key:str):
    if storage_backend == 'local':
        # Return absolute URL so it works regardless of frontend configuration
        # Frontend might be configured for Java (port 8080) but images are on Python (port 8000)
        base_url = os.getenv("BASE_URL", "http://localhost:8000")
        path = f"/{key}" if not key.startswith("/") else key
        return f"{base_url}{path}"
    elif storage_backend == 'aws':
        return generate_presigned_url(s3_key=key)


def upload_image(file, key, upload_type='folder'):
    """
    Upload image and return the local file path for later processing.
    Returns: Local file path (for background processing)
    """
    if storage_backend == 'local': 
        return upload_folder_to_local(file, key, upload_type)

    elif storage_backend == 'aws':
        upload_folder_to_bucket(file, key, upload_type)
        return key  # S3 key can be used to retrieve later 
