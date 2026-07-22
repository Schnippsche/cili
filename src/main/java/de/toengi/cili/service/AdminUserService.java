package de.toengi.cili.service;

import de.toengi.cili.dto.common.PageResponse;
import de.toengi.cili.dto.group.GroupDto;
import de.toengi.cili.dto.user.CreateUserRequest;
import de.toengi.cili.dto.user.UpdateUserRequest;
import de.toengi.cili.dto.user.UserDto;
import de.toengi.cili.exception.ConflictException;
import de.toengi.cili.exception.ResourceNotFoundException;
import de.toengi.cili.mapper.GroupMapper;
import de.toengi.cili.mapper.UserMapper;
import de.toengi.cili.model.entity.User;
import de.toengi.cili.model.enums.UserRole;
import de.toengi.cili.repository.UserGroupMembershipRepository;
import de.toengi.cili.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserGroupMembershipRepository membershipRepository;
    private final GroupMapper groupMapper;

    @Transactional(readOnly = true)
    public PageResponse<UserDto> listUsers(int page, int size) {
        Page<User> result = userRepository.findAll(
                PageRequest.of(page, size, Sort.by("username")));
        return new PageResponse<>(
                result.map(userMapper::toDto).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public UserDto getUser(Long id) {
        return userMapper.toDto(findById(id));
    }

    @Transactional
    public UserDto createUser(CreateUserRequest req) {
        if (userRepository.existsByUsername(req.username()))
            throw new ConflictException("Username already taken: " + req.username());
        if (userRepository.existsByEmail(req.email()))
            throw new ConflictException("Email already registered: " + req.email());

        UserRole role = parseRole(req.role(), UserRole.USER);
        User user = User.builder()
                .username(req.username())
                .email(req.email())
                .passwordHash(passwordEncoder.encode(req.password()))
                .displayName(req.displayName())
                .memberId(req.memberId())
                .url(req.url())
                .phone(req.phone())
                .role(role)
                .build();
        UserDto saved = userMapper.toDto(userRepository.save(user));
        log.info("User erstellt: id={} username='{}' email='{}' role={}", saved.id(), saved.username(), saved.email(), role);
        return saved;
    }

    @Transactional
    public UserDto updateUser(Long id, UpdateUserRequest req) {
        User user = findById(id);

        if (StringUtils.hasText(req.email()) && !req.email().equals(user.getEmail())) {
            if (userRepository.existsByEmail(req.email()))
                throw new ConflictException("Email already registered: " + req.email());
            user.setEmail(req.email());
        }
        if (StringUtils.hasText(req.password())) {
            user.setPasswordHash(passwordEncoder.encode(req.password()));
        }
        if (StringUtils.hasText(req.displayName())) {
            user.setDisplayName(req.displayName());
        }
        if (req.memberId() != null) {
            user.setMemberId(req.memberId());
        }
        if (StringUtils.hasText(req.url())) {
            user.setUrl(req.url());
        }
        if (StringUtils.hasText(req.phone())) {
            user.setPhone(req.phone());
        }
        if (req.active() != null) {
            user.setActive(req.active());
        }
        if (StringUtils.hasText(req.role())) {
            user.setRole(parseRole(req.role(), user.getRole()));
        }
        UserDto result = userMapper.toDto(userRepository.save(user));
        log.info("User aktualisiert: id={} username='{}' passwort={} active={} role={}",
                id, user.getUsername(),
                StringUtils.hasText(req.password()) ? "(geändert)" : "-",
                req.active() != null ? req.active() : "-",
                StringUtils.hasText(req.role()) ? req.role().toUpperCase() : "-");
        return result;
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        userRepository.deleteById(id);
        log.info("User gelöscht: id={} username='{}'", id, user.getUsername());
    }

    @Transactional(readOnly = true)
    public List<GroupDto> listGroups(Long userId) {
        findById(userId);
        return membershipRepository.findByUserId(userId).stream()
                .map(m -> groupMapper.toDto(m.getGroup()))
                .toList();
    }

    private User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private UserRole parseRole(String role, UserRole fallback) {
        if (!StringUtils.hasText(role)) return fallback;
        try { return UserRole.valueOf(role.toUpperCase()); }
        catch (IllegalArgumentException e) {
            log.warn("Unknown role '{}', using fallback '{}'", role, fallback);
            return fallback;
        }
    }
}
