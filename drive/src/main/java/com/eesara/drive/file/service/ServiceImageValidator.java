package com.eesara.drive.file.service;
import com.eesara.drive.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.IIOException;
import java.io.*;
import java.util.Set;
@Service
public class ServiceImageValidator {
 @Value("${wishes.max-image-size-mb:10}") private long maxMb;
 private static final Set<String> ALLOWED=Set.of("image/jpeg","image/png","image/webp");
 public String validate(MultipartFile file)throws IOException{
  if(file.isEmpty())throw new ApiException(HttpStatus.BAD_REQUEST,"EMPTY_FILE","The uploaded file is empty.");
  if(file.getSize()>maxMb*1024*1024)throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,"FILE_TOO_LARGE","The image exceeds the "+maxMb+" MB limit.");
  byte[] h;try(InputStream in=file.getInputStream()){h=in.readNBytes(16);}String actual=detect(h);
  if(actual==null)throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"UNSUPPORTED_MEDIA_TYPE","Only JPEG, PNG, and WebP images are accepted.");
  if(!ALLOWED.contains(file.getContentType())||!actual.equals(file.getContentType()))throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"MIME_TYPE_MISMATCH","The declared MIME type does not match the image data.");
  if(actual.equals("image/webp")&&!validWebp(h,file.getSize()))throw malformed();
  if(!actual.equals("image/webp")){try(InputStream in=file.getInputStream()){if(ImageIO.read(in)==null)throw malformed();}catch(IIOException ex){throw malformed();}}
  return actual;
 }
 private String detect(byte[] b){if(b.length>=3&&(b[0]&255)==0xff&&(b[1]&255)==0xd8&&(b[2]&255)==0xff)return "image/jpeg";
  if(b.length>=8&&(b[0]&255)==0x89&&b[1]=='P'&&b[2]=='N'&&b[3]=='G'&&(b[4]&255)==13&&(b[5]&255)==10&&(b[6]&255)==26&&(b[7]&255)==10)return "image/png";
  if(b.length>=12&&new String(b,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")&&new String(b,8,4,java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP"))return "image/webp";return null;}
 private boolean validWebp(byte[] b,long size){if(b.length<16)return false;long declared=(b[4]&255L)|((b[5]&255L)<<8)|((b[6]&255L)<<16)|((b[7]&255L)<<24);String chunk=new String(b,12,4,java.nio.charset.StandardCharsets.US_ASCII);return declared+8==size&&Set.of("VP8 ","VP8L","VP8X").contains(chunk);}
 private ApiException malformed(){return new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"MALFORMED_IMAGE","The uploaded image is malformed.");}
}
