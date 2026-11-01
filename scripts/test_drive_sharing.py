"""
FamilyVault — Task 1: Google Drive Sharing Spike CLI Verification Script

This script tests the core hypothesis on Google Drive API directly:
Can User B access and download/stream a file uploaded by User A when User B only has
the `https://www.googleapis.com/auth/drive.file` scope, after User A shares the file
with User B via `permissions.create(role='reader', type='user')`?

Usage:
  Option 1 (Direct access tokens):
    python scripts/test_drive_sharing.py --token-a <USER_A_TOKEN> --token-b <USER_B_TOKEN> --email-b <USER_B_EMAIL>

  Option 2 (Interactive OAuth with client_secret.json):
    python scripts/test_drive_sharing.py --client-secrets <PATH_TO_WEB_CLIENT_JSON> --email-b <USER_B_EMAIL>
"""

import argparse
import json
import os
import sys
import time
import urllib.parse
import urllib.request
import urllib.error

DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

def make_request(url, headers=None, data=None, method="GET"):
    headers = headers or {}
    req = urllib.request.Request(url, headers=headers, data=data, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            content = resp.read()
            return resp.status, dict(resp.headers), content
    except urllib.error.HTTPError as e:
        content = e.read()
        return e.code, dict(e.headers), content
    except Exception as e:
        return 0, {}, str(e).encode("utf-8")

def find_or_create_folder(token, folder_name="FamilyVault"):
    print(f"\n[User A] Checking for '{folder_name}' folder...")
    q = urllib.parse.quote(f"name = '{folder_name}' and mimeType = 'application/vnd.google-apps.folder' and trashed = false")
    status, headers, body = make_request(
        f"https://www.googleapis.com/drive/v3/files?q={q}&fields=files(id,name)",
        headers={"Authorization": f"Bearer {token}"}
    )
    if status == 200:
        data = json.loads(body.decode("utf-8"))
        files = data.get("files", [])
        if files:
            folder_id = files[0]["id"]
            print(f"[User A] Found existing folder: {folder_id}")
            return folder_id

    # Create folder
    print(f"[User A] Creating '{folder_name}' folder...")
    create_body = json.dumps({"name": folder_name, "mimeType": "application/vnd.google-apps.folder"}).encode("utf-8")
    status, headers, body = make_request(
        "https://www.googleapis.com/drive/v3/files",
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
        data=create_body,
        method="POST"
    )
    if status in (200, 201):
        folder_id = json.loads(body.decode("utf-8"))["id"]
        print(f"[User A] Created folder: {folder_id}")
        return folder_id
    else:
        raise RuntimeError(f"Failed to create folder: {status} {body.decode('utf-8', errors='replace')}")

def upload_resumable(token, folder_id, file_path):
    file_size = os.path.getsize(file_path)
    file_name = os.path.basename(file_path)
    print(f"\n[User A] Starting resumable upload for '{file_name}' ({file_size} bytes)...")

    # Initiate
    metadata = json.dumps({"name": file_name, "mimeType": "video/mp4", "parents": [folder_id]}).encode("utf-8")
    init_headers = {
        "Authorization": f"Bearer {token}",
        "X-Upload-Content-Type": "video/mp4",
        "X-Upload-Content-Length": str(file_size),
        "Content-Type": "application/json; charset=UTF-8"
    }
    status, headers, body = make_request(
        "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable",
        headers=init_headers,
        data=metadata,
        method="POST"
    )
    location = headers.get("Location") or headers.get("location")
    if not location:
        raise RuntimeError(f"Failed to initiate resumable upload: {status} {body.decode('utf-8', errors='replace')}")

    # Upload
    with open(file_path, "rb") as f:
        file_bytes = f.read()

    start_time = time.time()
    upload_headers = {
        "Content-Range": f"bytes 0-{file_size - 1}/{file_size}",
        "Content-Type": "video/mp4"
    }
    status, headers, body = make_request(location, headers=upload_headers, data=file_bytes, method="PUT")
    elapsed = time.time() - start_time
    if status in (200, 201):
        file_id = json.loads(body.decode("utf-8"))["id"]
        speed_mbps = (file_size / (1024 * 1024)) / elapsed if elapsed > 0 else 0
        print(f"[User A] Upload complete in {elapsed:.2f}s ({speed_mbps:.2f} MB/s). File ID: {file_id}")
        return file_id
    else:
        raise RuntimeError(f"Upload failed: {status} {body.decode('utf-8', errors='replace')}")

def share_file(token, file_id, target_email):
    print(f"\n[User A] Granting reader permission to {target_email} on file {file_id}...")
    perm = json.dumps({"role": "reader", "type": "user", "emailAddress": target_email}).encode("utf-8")
    status, headers, body = make_request(
        f"https://www.googleapis.com/drive/v3/files/{file_id}/permissions?sendNotificationEmail=false",
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
        data=perm,
        method="POST"
    )
    print(f"[User A] Share response HTTP {status}: {body.decode('utf-8', errors='replace')}")
    return status in (200, 201)

def test_user_b_metadata(token_b, file_id):
    print(f"\n[User B] Testing metadata call: files.get(fileId={file_id})...")
    fields = "id,name,size,mimeType,webContentLink,thumbnailLink,permissions"
    status, headers, body = make_request(
        f"https://www.googleapis.com/drive/v3/files/{file_id}?fields={fields}",
        headers={"Authorization": f"Bearer {token_b}"}
    )
    print(f"[User B] Metadata response HTTP {status}")
    print(f"[User B] Body:\n{body.decode('utf-8', errors='replace')}")
    return status == 200

def test_user_b_stream(token_b, file_id):
    print(f"\n[User B] Testing stream/download: files.get(alt=media)...")
    start_time = time.time()
    status, headers, body = make_request(
        f"https://www.googleapis.com/drive/v3/files/{file_id}?alt=media",
        headers={"Authorization": f"Bearer {token_b}"}
    )
    elapsed = time.time() - start_time
    print(f"[User B] Stream response HTTP {status} in {elapsed:.3f}s. Received {len(body)} bytes.")
    if status == 200:
        print("[User B] SUCCESS: Video streamed successfully with drive.file scope!")
        return True
    else:
        print(f"[User B] FAILURE: Video streaming returned HTTP {status}.")
        print(f"[User B] Error details: {body.decode('utf-8', errors='replace')}")
        return False

def main():
    parser = argparse.ArgumentParser(description="Google Drive Sharing Spike Verification")
    parser.add_argument("--token-a", help="OAuth access token for User A (uploader)")
    parser.add_argument("--token-b", help="OAuth access token for User B (viewer)")
    parser.add_argument("--email-b", required=True, help="Google account email of User B")
    parser.add_argument("--video-file", help="Path to test MP4 video (default: generates small sample)")
    args = parser.parse_args()

    if not args.token_a or not args.token_b:
        print("Please supply both --token-a and --token-b, or run via the Android spike app.")
        sys.exit(1)

    video_path = args.video_file
    if not video_path or not os.path.exists(video_path):
        video_path = "temp_test_video.mp4"
        with open(video_path, "wb") as f:
            f.write(b"\x00" * (1024 * 512))  # 512 KB placeholder
        print(f"Created temporary test file: {video_path}")

    try:
        folder_id = find_or_create_folder(args.token_a)
        file_id = upload_resumable(args.token_a, folder_id, video_path)
        share_ok = share_file(args.token_a, file_id, args.email_b)

        print("\n" + "=" * 50)
        print("CRITICAL HYPOTHESIS TEST: USER B ACCESS")
        print("=" * 50)
        meta_ok = test_user_b_metadata(args.token_b, file_id)
        stream_ok = test_user_b_stream(args.token_b, file_id)

        print("\n" + "=" * 50)
        print("RESULTS SUMMARY:")
        print(f"User A upload: SUCCESS (fileId: {file_id})")
        print(f"User A share to {args.email_b}: {'SUCCESS' if share_ok else 'FAILED'}")
        print(f"User B metadata (drive.file): {'SUCCESS' if meta_ok else 'FAILED'}")
        print(f"User B stream (drive.file): {'SUCCESS' if stream_ok else 'FAILED'}")
        print("=" * 50)
    finally:
        if os.path.exists("temp_test_video.mp4"):
            os.remove("temp_test_video.mp4")

if __name__ == "__main__":
    main()
