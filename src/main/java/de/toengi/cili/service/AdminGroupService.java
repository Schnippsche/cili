package de.toengi.cili.service;

import de.toengi.cili.dto.common.PageResponse;
import de.toengi.cili.dto.group.CreateGroupRequest;
import de.toengi.cili.dto.group.GroupDto;
import de.toengi.cili.dto.group.UpdateGroupRequest;
import de.toengi.cili.dto.user.UserDto;
import de.toengi.cili.exception.ConflictException;
import de.toengi.cili.exception.ResourceNotFoundException;
import de.toengi.cili.mapper.GroupMapper;
import de.toengi.cili.mapper.UserMapper;
import de.toengi.cili.model.entity.RightsGroup;
import de.toengi.cili.model.entity.UserGroupMembership;
import de.toengi.cili.model.entity.UserGroupMembershipId;
import de.toengi.cili.repository.RightsGroupRepository;
import de.toengi.cili.repository.UserGroupMembershipRepository;
import de.toengi.cili.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminGroupService {

    private final RightsGroupRepository groupRepository;
    private final UserGroupMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final GroupMapper groupMapper;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public PageResponse<GroupDto> listGroups(int page, int size) {
        Page<RightsGroup> result = groupRepository.findAll(
                PageRequest.of(page, size, Sort.by("name")));
        return new PageResponse<>(
                result.map(groupMapper::toDto).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public GroupDto getGroup(Long id) {
        return groupMapper.toDto(findById(id));
    }

    @Transactional
    public GroupDto createGroup(CreateGroupRequest req) {
        if (groupRepository.existsByName(req.name()))
            throw new ConflictException("Group name already exists: " + req.name());
        RightsGroup group = RightsGroup.builder()
                .name(req.name())
                .description(req.description())
                .build();
        GroupDto saved = groupMapper.toDto(groupRepository.save(group));
        log.info("Gruppe erstellt: id={} name='{}'", saved.id(), req.name());
        return saved;
    }

    @Transactional
    public GroupDto updateGroup(Long id, UpdateGroupRequest req) {
        RightsGroup group = findById(id);
        String oldName = group.getName();
        if (StringUtils.hasText(req.name()) && !req.name().equals(group.getName())) {
            if (groupRepository.existsByName(req.name()))
                throw new ConflictException("Group name already exists: " + req.name());
            group.setName(req.name());
        }
        if (req.description() != null) group.setDescription(req.description());
        GroupDto result = groupMapper.toDto(groupRepository.save(group));
        log.info("Gruppe aktualisiert: id={} '{}' -> name='{}'", id, oldName, group.getName());
        return result;
    }

    @Transactional
    public void deleteGroup(Long id) {
        RightsGroup group = groupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group", id));
        groupRepository.deleteById(id);
        log.info("Gruppe gelöscht: id={} name='{}'", id, group.getName());
    }

    @Transactional
    public void addMember(Long groupId, Long userId) {
        RightsGroup group = findById(groupId);
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        var membershipId = new UserGroupMembershipId(userId, groupId);
        if (!membershipRepository.existsById(membershipId)) {
            membershipRepository.save(UserGroupMembership.builder()
                    .id(membershipId).user(user).group(group).build());
            log.info("Gruppenmitglied hinzugefügt: group={} '{}' user={} '{}'",
                    groupId, group.getName(), userId, user.getUsername());
        }
    }

    @Transactional
    public void removeMember(Long groupId, Long userId) {
        var membershipId = new UserGroupMembershipId(userId, groupId);
        if (membershipRepository.existsById(membershipId)) {
            membershipRepository.deleteById(membershipId);
            log.info("Gruppenmitglied entfernt: group={} user={}", groupId, userId);
        }
    }

    @Transactional(readOnly = true)
    public List<UserDto> listMembers(Long groupId) {
        findById(groupId);
        return membershipRepository.findByGroupId(groupId).stream()
                .map(m -> userMapper.toDto(m.getUser()))
                .toList();
    }

    private RightsGroup findById(Long id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group", id));
    }
}
