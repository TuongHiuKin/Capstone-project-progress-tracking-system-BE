package com.capstone.tracking.group;

import com.capstone.tracking.WorkflowTestSupport;
import com.capstone.tracking.user.Role;
import com.capstone.tracking.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests verifying Issue #14: GROUP_LEADER Lock and Unlock group permissions and business rules.
 */
class GroupLeaderLockGroupIntegrationTest extends WorkflowTestSupport {

    private final String sem = "LockSem-" + suffix;

    private UUID createGroup(User leader) throws Exception {
        return UUID.fromString(body(postJson("/api/v1/groups", leader, Map.of("semester", sem))
                .andExpect(status().isCreated())).get("id").asText());
    }

    private void addMember(User leader, UUID groupId, User student) throws Exception {
        UUID inviteId = UUID.fromString(body(postJson("/api/v1/groups/" + groupId + "/invites", leader,
                Map.of("userId", student.getId())).andExpect(status().isCreated())).get("id").asText());
        postJson("/api/v1/invites/" + inviteId + "/accept", student, Map.of()).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Leader can lock own group successfully when eligible")
    void leaderLocksOwnGroupSuccessfullyWhenEligible() throws Exception {
        User leader = user("lock-lead-1", Role.STUDENT);
        User m1 = user("lock-m1-1", Role.STUDENT);
        User m2 = user("lock-m2-1", Role.STUDENT);

        UUID groupId = createGroup(leader);
        addMember(leader, groupId, m1);
        addMember(leader, groupId, m2);

        postJson("/api/v1/groups/" + groupId + "/lock", leader, Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(true))
                .andExpect(jsonPath("$.lockedByAdmin").value(false));

        // Leader can unlock own group
        postJson("/api/v1/groups/" + groupId + "/unlock", leader, Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(false));
    }

    @Test
    @DisplayName("Leader of Group A cannot lock Group B (returns 403 Forbidden)")
    void leaderCannotLockAnotherGroup() throws Exception {
        User leaderA = user("lock-leadA", Role.STUDENT);
        User m1A = user("lock-m1A", Role.STUDENT);
        User m2A = user("lock-m2A", Role.STUDENT);

        User leaderB = user("lock-leadB", Role.STUDENT);
        User m1B = user("lock-m1B", Role.STUDENT);
        User m2B = user("lock-m2B", Role.STUDENT);

        UUID groupA = createGroup(leaderA);
        addMember(leaderA, groupA, m1A);
        addMember(leaderA, groupA, m2A);

        UUID groupB = createGroup(leaderB);
        addMember(leaderB, groupB, m1B);
        addMember(leaderB, groupB, m2B);

        // Leader A tries to lock Group B -> 403 Forbidden
        postJson("/api/v1/groups/" + groupB + "/lock", leaderA, Map.of())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Normal student member cannot lock group (returns 403 Forbidden)")
    void normalStudentMemberCannotLockGroup() throws Exception {
        User leader = user("lock-lead-mem", Role.STUDENT);
        User member = user("lock-member-only", Role.STUDENT);
        User m2 = user("lock-m2-mem", Role.STUDENT);

        UUID groupId = createGroup(leader);
        addMember(leader, groupId, member);
        addMember(leader, groupId, m2);

        // Member (non-leader) tries to lock -> 403 Forbidden
        postJson("/api/v1/groups/" + groupId + "/lock", member, Map.of())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Ineligible group status (e.g. COMPLETED) returns 409 Conflict, not 500")
    void cannotLockGroupWithIneligibleStatus() throws Exception {
        User leader = user("lock-lead-arch", Role.STUDENT);
        UUID groupId = createGroup(leader);

        // Set status to COMPLETED
        StudentGroup group = studentGroupRepository.findById(groupId).orElseThrow();
        group.setStatus(GroupStatus.COMPLETED);
        studentGroupRepository.save(group);

        postJson("/api/v1/groups/" + groupId + "/lock", leader, Map.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Cannot change lock on a group that is completed")));
    }

    @Test
    @DisplayName("Leader cannot unlock a group locked by Admin (returns 403 Forbidden)")
    void leaderCannotUnlockGroupLockedByAdmin() throws Exception {
        User leader = user("lock-lead-adm", Role.STUDENT);
        User admin = user("lock-admin-usr", Role.ADMIN);

        UUID groupId = createGroup(leader);

        // Admin locks the group
        postJson("/api/v1/groups/" + groupId + "/lock", admin, Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(true))
                .andExpect(jsonPath("$.lockedByAdmin").value(true));

        // Leader tries to unlock -> 403 Forbidden
        postJson("/api/v1/groups/" + groupId + "/unlock", leader, Map.of())
                .andExpect(status().isForbidden());

        // Admin can unlock
        postJson("/api/v1/groups/" + groupId + "/unlock", admin, Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(false));
    }
}
