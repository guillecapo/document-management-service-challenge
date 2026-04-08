package com.clara.ops.challenge.document_management_service_challenge.infrastructure.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BucketInitializer implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(BucketInitializer.class);

  private final MinioClient minioClient;
  private final MinioProperties properties;

  @Override
  public void run(ApplicationArguments args) throws Exception {
    String bucket = properties.bucketName();
    boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
    if (!exists) {
      minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
      log.info("Bucket '{}' created.", bucket);
    } else {
      log.info("Bucket '{}' already exists.", bucket);
    }
  }
}
