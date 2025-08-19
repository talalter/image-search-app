import boto3  # type: ignore
from dotenv import load_dotenv # type: ignore
import os

BUCKET_NAME = "images-search-app"
load_dotenv()

storage_backend = os.getenv("STORAGE_BACKEND").lower()
print(storage_backend)

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
    base_folder = os.getcwd()
    folder_path = os.path.join(base_folder, key)

    os.makedirs(os.path.dirname(folder_path), exist_ok=True)

    if upload_type == 'folder':
        file.file.seek(0)
        with open(folder_path, 'wb') as f:
            f.write(file.file.read())

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
        return key
    elif storage_backend == 'aws':
        return generate_presigned_url(s3_key=key)


def upload_image(file, key, upload_type='folder'):

    if storage_backend == 'local': 
        upload_folder_to_local(file, key, upload_type)


    elif storage_backend == 'aws':
        print("Uploading to AWS S3") 
        upload_folder_to_bucket(file, key, upload_type) 
