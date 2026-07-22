package de.toengi.cili.controller.admin;

import de.toengi.cili.dto.common.PageResponse;
import de.toengi.cili.dto.group.GroupDto;
import de.toengi.cili.dto.user.CreateUserRequest;
import de.toengi.cili.dto.user.UpdateUserRequest;
import de.toengi.cili.dto.user.UserDto;
import de.toengi.cili.service.AdminUserService;
import de.toengi.cili.service.UserLabelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final UserLabelService userLabelService;

    @GetMapping
    public ResponseEntity<PageResponse<UserDto>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminUserService.listUsers(page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(adminUserService.getUser(id));
    }

    @PostMapping
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminUserService.createUser(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserDto> updateUser(@PathVariable Long id,
                                              @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(adminUserService.updateUser(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        adminUserService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/groups")
    public ResponseEntity<List<GroupDto>> listUserGroups(@PathVariable Long id) {
        return ResponseEntity.ok(adminUserService.listGroups(id));
    }

    @GetMapping("/{id}/labels")
    public ResponseEntity<StreamingResponseBody> generateLabels(@PathVariable Long id) throws IOException {
        Path pdfPath = userLabelService.generateLabelSheet(id);
        StreamingResponseBody body = out -> {
            try {
                Files.copy(pdfPath, out);
            } finally {
                Files.deleteIfExists(pdfPath);
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(Files.size(pdfPath))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(body);
    }
}
