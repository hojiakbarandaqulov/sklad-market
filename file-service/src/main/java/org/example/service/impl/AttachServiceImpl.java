package org.example.service.impl;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import org.example.dto.AttachDto;
import org.example.entity.Attach;
import org.example.exp.AppBadException;
import org.example.repository.AttachRepository;
import org.example.service.AttachService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class AttachServiceImpl implements AttachService {
    private final AttachRepository attachRepository;
    private final MinioClient minioClient;

    @Value("${aws.bucket-name}")
    private String bucketName;

    @Value("${aws.url}")
    private String url;

    @Override
    public AttachDto upload(MultipartFile file) {
        try {
            String originalName = file.getOriginalFilename();
            assert originalName != null;
            String extension = originalName.substring(originalName.lastIndexOf(".") + 1);
            String key = UUID.randomUUID().toString();
            try (InputStream inputStream = file.getInputStream()){
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(key)
                                .stream(inputStream, file.getSize(), -1)
                                .contentType(file.getContentType())
                                .build()
                );
            }
            Attach attach = new Attach();
            attach.setId(key + "." + extension);
            attach.setOriginalName(originalName);
            attach.setExtension(extension);
            attach.setSize(file.getSize());
            attach.setPath(bucketName + "/" + key);
            attach.setCreatedDate(LocalDateTime.now());
            attachRepository.save(attach);

            AttachDto attachDto = new AttachDto();
            attachDto.setId(attach.getId());
            attachDto.setUrl(bucketName + "/" + key);
            return attachDto;
        }catch (Exception e){
            throw new AppBadException("");
        }
    }
}
