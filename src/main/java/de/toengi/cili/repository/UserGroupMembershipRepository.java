package de.toengi.cili.repository;

import de.toengi.cili.model.entity.UserGroupMembership;
import de.toengi.cili.model.entity.UserGroupMembershipId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserGroupMembershipRepository extends JpaRepository<UserGroupMembership, UserGroupMembershipId> {

    @Query("SELECT m FROM UserGroupMembership m WHERE m.id.userId = :userId")
    List<UserGroupMembership> findByUserId(@Param("userId") Long userId);

    @Query("SELECT m FROM UserGroupMembership m WHERE m.id.groupId = :groupId")
    List<UserGroupMembership> findByGroupId(@Param("groupId") Long groupId);
}
