package com.eesara.drive.apikey;
import com.eesara.drive.apikey.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/admin/api-keys") @RequiredArgsConstructor
public class ApiKeyAdminController {
 private final ApiKeyAdminService service;
 @PostMapping public CreateApiKeyResponse create(@Valid @RequestBody CreateApiKeyRequest request){return service.create(request);}
 @GetMapping public List<ApiKeyResponse> list(){return service.list();}
 @PutMapping("/{id}/active") public ApiKeyResponse active(@PathVariable long id,@RequestParam boolean active){return service.active(id,active);}
 @DeleteMapping("/{id}") public ApiKeyResponse revoke(@PathVariable long id){return service.active(id,false);}
}
