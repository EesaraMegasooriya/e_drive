package com.eesara.drive.file.service;
import com.eesara.drive.common.ApiException;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import static org.assertj.core.api.Assertions.*;
class ServiceImageValidatorTests {
 private ServiceImageValidator validator;
 @BeforeEach void setup(){validator=new ServiceImageValidator();ReflectionTestUtils.setField(validator,"maxMb",10L);}
 @Test void acceptsValidPng()throws Exception{assertThat(validator.validate(file("image/png",png()))).isEqualTo("image/png");}
 @Test void rejectsMismatchedMime()throws Exception{assertThatThrownBy(()->validator.validate(file("image/jpeg",png()))).isInstanceOf(ApiException.class).extracting("code").isEqualTo("MIME_TYPE_MISMATCH");}
 @Test void rejectsInvalidSignature(){assertThatThrownBy(()->validator.validate(file("image/png","not-image".getBytes()))).isInstanceOf(ApiException.class).extracting("code").isEqualTo("UNSUPPORTED_MEDIA_TYPE");}
 @Test void rejectsOversize(){byte[] bytes=new byte[10*1024*1024+1];bytes[0]=(byte)0xff;bytes[1]=(byte)0xd8;bytes[2]=(byte)0xff;assertThatThrownBy(()->validator.validate(file("image/jpeg",bytes))).isInstanceOf(ApiException.class).extracting("code").isEqualTo("FILE_TOO_LARGE");}
 @Test void rejectsEmpty(){assertThatThrownBy(()->validator.validate(file("image/png",new byte[0]))).isInstanceOf(ApiException.class).extracting("code").isEqualTo("EMPTY_FILE");}
 private MockMultipartFile file(String type,byte[] data){return new MockMultipartFile("file","image",type,data);}
 private byte[] png()throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",out);return out.toByteArray();}
}
