package de.toengi.cili.controller.admin;

import de.toengi.cili.dto.common.PageResponse;
import de.toengi.cili.dto.group.*;
import de.toengi.cili.dto.user.UserDto;
import de.toengi.cili.service.AdminGroupService;

import java.util.List;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/groups")
@RequiredArgsConstructor
public class AdminGroupController {

    private final AdminGroupService adminGroupService;

    @GetMapping
    public ResponseEntity<PageResponse<GroupDto>> listGroups(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminGroupService.listGroups(page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GroupDto> getGroup(@PathVariable Long id) {
        return ResponseEntity.ok(adminGroupService.getGroup(id));
    }

    @PostMapping
    public ResponseEntity<GroupDto> createGroup(@Valid @RequestBody CreateGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminGroupService.createGroup(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GroupDto> updateGroup(@PathVariable Long id,
                                                @Valid @RequestBody UpdateGroupRequest request) {
        return ResponseEntity.ok(adminGroupService.updateGroup(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long id) {
        adminGroupService.deleteGroup(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<UserDto>> listMembers(@PathVariable Long id) {
        return ResponseEntity.ok(adminGroupService.listMembers(id));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<Void> addMember(@PathVariable Long id,
                                          @Valid @RequestBody GroupMemberRequest request) {
        adminGroupService.addMember(id, request.userId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        adminGroupService.removeMember(id, userId);
        return ResponseEntity.noContent().build();
    }
}
