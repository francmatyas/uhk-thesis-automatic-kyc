package com.francmatyas.uhk_thesis_automatic_kyc_api.r2_storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.io.InputStream;
import java.time.Duration;

@Service
public class R2StorageService {

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final S3Presigner workerPresigner;
    private final String bucket;
    private final String publicBaseUrl;

    public R2StorageService(
            S3Client r2S3Client,
            S3Presigner r2S3Presigner,
            S3Presigner r2S3WorkerPresigner,
            @Value("${storage.s3.bucket}") String bucket,
            @Value("${storage.s3.public-base-url}") String publicBaseUrl
    ) {
        this.s3Client = r2S3Client;
        this.presigner = r2S3Presigner;
        this.workerPresigner = r2S3WorkerPresigner;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl;
    }

    // ---------- přímý upload/download přes backend ----------

    public void upload(String key, byte[] bytes, String contentType) {
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        s3Client.putObject(put, RequestBody.fromBytes(bytes));
    }

    public InputStream download(String key) {
        GetObjectRequest get = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        return s3Client.getObject(get);
    }

    public void delete(String key) {
        s3Client.deleteObject(b -> b.bucket(bucket).key(key));
    }

    // ---------- presigned URL pro frontend ----------

    public String createUploadUrl(String key, String contentType, Duration ttl) {
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(objectRequest)
                .build();

        PresignedPutObjectRequest presigned = presigner.presignPutObject(presignRequest);
        return presigned.url().toString();
    }

    public String createDownloadUrl(String key, Duration ttl) {
        GetObjectRequest objectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(objectRequest)
                .build();

        PresignedGetObjectRequest presigned = presigner.presignGetObject(presignRequest);
        return presigned.url().toString();
    }

    // výchozí pohodlné hodnoty
    public String createUploadUrl(String key, String contentType) {
        return createUploadUrl(key, contentType, Duration.ofMinutes(15));
    }

    public String createDownloadUrl(String key) {
        return createDownloadUrl(key, Duration.ofHours(1));
    }

    // ---------- presigned URL pro interního workera (Docker síť) ----------

    public String createWorkerDownloadUrl(String key, Duration ttl) {
        GetObjectRequest objectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(objectRequest)
                .build();

        PresignedGetObjectRequest presigned = workerPresigner.presignGetObject(presignRequest);
        return presigned.url().toString();
    }

    public String createWorkerDownloadUrl(String key) {
        return createWorkerDownloadUrl(key, Duration.ofHours(1));
    }

    public String publicUrlForKey(String key) {
        return publicBaseUrl + "/" + key;
    }
}