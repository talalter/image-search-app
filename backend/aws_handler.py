import boto3  # type: ignore
from dotenv import load_dotenv # type: ignore
import os


load_dotenv()
s3 = boto3.client(
    's3',
    aws_access_key_id=os.getenv("AWS_ACCESS_KEY_ID"),
    aws_secret_access_key=os.getenv("AWS_SECRET_ACCESS_KEY"),
    region_name=os.getenv("AWS_REGION")
)

BUCKET_NAME = "images-search-app"



def upload_folder_to_bucket(file, key, upload_type='folder', bucket_name=BUCKET_NAME):
    """Uploads a folder or file to an S3 bucket."""
    if upload_type == 'folder':
        file.file.seek(0)
        s3.upload_fileobj(
            file.file,
            bucket_name,
            key,
            ExtraArgs={"ContentType": file.content_type}
        )
    elif upload_type == 'faiss':
        s3.upload_file(file, bucket_name, key)


def generate_presigned_url(s3_key: str, bucket_name=BUCKET_NAME, expiration=60) -> str:
    """Generate a presigned URL for an object in S3."""
    return s3.generate_presigned_url(
        ClientMethod='get_object',
        Params={'Bucket': bucket_name, 'Key': s3_key},
        ExpiresIn=expiration
    )