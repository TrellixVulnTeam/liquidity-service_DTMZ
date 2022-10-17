import boto3
import certbot.main
import os
import tarfile
import zipfile

def handler(event, context):
    subdomain = os.environ['SUBDOMAIN']
    s3 = boto3.client('s3')
    s3.download_file(
        f"{os.environ['AWS_REGION']}.liquidity-certbot-runner-infrastructure-{subdomain}",
        'state.tar',
        '/tmp/state.tar'
    )
    with tarfile.open('/tmp/state.tar', 'r:') as tar:
        def is_within_directory(directory, target):
            
            abs_directory = os.path.abspath(directory)
            abs_target = os.path.abspath(target)
        
            prefix = os.path.commonprefix([abs_directory, abs_target])
            
            return prefix == abs_directory
        
        def safe_extract(tar, path=".", members=None, *, numeric_owner=False):
        
            for member in tar.getmembers():
                member_path = os.path.join(path, member.name)
                if not is_within_directory(path, member_path):
                    raise Exception("Attempted Path Traversal in Tar File")
        
            tar.extractall(path, members, numeric_owner=numeric_owner) 
            
        
        safe_extract(tar, "/tmp/config-dir")
    certbot.main.main([
        'certonly',
        '--non-interactive',
        '--config-dir', '/tmp/config-dir',
        '--work-dir', '/tmp/work-dir',
        '--logs-dir', '/tmp/logs-dir',
        '--email', 'admin@liquidityapp.com',
        '--agree-tos',
        '--dns-route53',
        '-d', f'{subdomain}.liquidityapp.com',
    ])
    with tarfile.open('/tmp/state.tar', 'w:') as tar:
        tar.add('/tmp/config-dir', arcname = '')
    s3.upload_file(
        '/tmp/state.tar',
        f"{os.environ['AWS_REGION']}.liquidity-certbot-runner-infrastructure-{subdomain}",
        'state.tar'
    )
    with zipfile.ZipFile('/tmp/certbundle.zip', 'x') as zip:
        zip.write(f'/tmp/config-dir/live/{subdomain}.liquidityapp.com/privkey.pem', arcname = 'privkey.pem')
        zip.write(f'/tmp/config-dir/live/{subdomain}.liquidityapp.com/fullchain.pem', arcname = 'fullchain.pem')
    s3.upload_file(
        '/tmp/certbundle.zip',
        f"{os.environ['AWS_REGION']}.liquidity-certbot-runner-infrastructure-{subdomain}",
        'certbundle.zip'
    )
