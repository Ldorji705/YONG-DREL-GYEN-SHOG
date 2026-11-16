package com.yongdrel.payment_proof_service.storage;
import org.springframework.beans.factory.annotation.Value; import org.springframework.stereotype.Component; import org.springframework.web.multipart.MultipartFile; import java.io.*; import java.nio.file.*; import java.util.UUID;
@Component public class PrivateProofStorage{
 private final Path root; public PrivateProofStorage(@Value("${app.storage.root}")String configured){root=Paths.get(configured).toAbsolutePath().normalize();try{Files.createDirectories(root);}catch(IOException e){throw new IllegalStateException("Private proof storage unavailable",e);}}
 public StoredFile save(MultipartFile file,String extension){String key=UUID.randomUUID()+extension;Path target=root.resolve(key).normalize();if(!target.getParent().equals(root))throw new IllegalStateException("Invalid storage path");try{file.transferTo(target);return new StoredFile(key);}catch(IOException e){throw new IllegalStateException("Proof storage failed",e);}}
 public byte[] read(String key){Path path=root.resolve(key).normalize();if(!path.getParent().equals(root))throw new SecurityException("Invalid storage path");try{return Files.readAllBytes(path);}catch(IOException e){throw new IllegalStateException("Proof unavailable",e);}}
 public record StoredFile(String key){}
}
