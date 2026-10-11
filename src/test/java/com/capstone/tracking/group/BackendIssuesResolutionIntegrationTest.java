package com.capstone.tracking.group;

import com.capstone.tracking.WorkflowTestSupport;
import com.capstone.tracking.user.Role;
import com.capstone.tracking.user.User;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BackendIssuesResolutionIntegrationTest extends WorkflowTestSupport {

    @Test
    @DisplayName("Issue #10: Role deserializer accepts 'LEADER' as alias for GROUP_LEADER")
    void roleDeserializerAcceptsLeaderAlias() throws Exception {
        Role parsed = objectMapper.readValue("\"LEADER\"", Role.class);
        assertThat(parsed).isEqualTo(Role.GROUP_LEADER);

        Role lowerParsed = objectMapper.readValue("\"leader\"", Role.class);
        assertThat(lowerParsed).isEqualTo(Role.GROUP_LEADER);
    }

    @Test
    @DisplayName("Issue #9: UserUpdateRequest allows null status (does not require @NotNull)")
    void userUpdateAllowsNullStatus() throws Exception {
        User admin = user("admin-user-upd", Role.ADMIN);
        User student = user("student-upd", Role.STUDENT);

        mockMvc.perform(put("/api/v1/users/" + student.getId())
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Updated Student Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Student Name"));
    }

    @Test
    @DisplayName("Issue #8: Unread notification count returns unread, count, and unreadCount")
    void unreadNotificationCountReturnsAllKeys() throws Exception {
        User student = user("student-notif", Role.STUDENT);

        getAs("/api/v1/notifications/unread-count", student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(0))
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    @DisplayName("Issue #7 & #6: GroupMemberResponse includes eligibility; /me/group and /groups/mine return active group")
    void groupMemberEligibilityAndMyGroupEndpoints() throws Exception {
        User studentNoGroup = user("no-group-std", Role.STUDENT);
        getAs("/api/v1/me/group", studentNoGroup).andExpect(status().isNoContent());
        getAs("/api/v1/groups/mine", studentNoGroup).andExpect(status().isNoContent());

        User leader = user("group-lead-issue7", Role.STUDENT);
        String groupJson = body(postJson("/api/v1/groups", leader, Map.of("semester", "S-" + suffix))
                .andExpect(status().isCreated())).toString();
        UUID groupId = UUID.fromString(objectMapper.readTree(groupJson).get("id").asText());

        // Test GET /api/v1/me/group
        JsonNode myGroup = body(getAs("/api/v1/me/group", leader).andExpect(status().isOk()));
        assertThat(myGroup.get("id").asText()).isEqualTo(groupId.toString());
        assertThat(myGroup.get("members").get(0).get("eligible").asBoolean()).isTrue();

        // Test GET /api/v1/groups/mine
        JsonNode groupsMine = body(getAs("/api/v1/groups/mine", leader).andExpect(status().isOk()));
        assertThat(groupsMine.get("id").asText()).isEqualTo(groupId.toString());
    }

    @Test
    @DisplayName("Issue #12: Students can view recruiting groups and active members before applying")
    void studentsCanViewRecruitingGroupBeforeApplying() throws Exception {
        User leader = user("recruiting-leader", Role.STUDENT);
        String groupJson = body(postJson("/api/v1/groups", leader, Map.of("semester", "S-" + suffix))
                .andExpect(status().isCreated())).toString();
        UUID groupId = UUID.fromString(objectMapper.readTree(groupJson).get("id").asText());

        User candidate = user("outsider-candidate", Role.STUDENT);

        // Outside candidate can view recruiting group details and members
        getAs("/api/v1/groups/" + groupId, candidate)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(groupId.toString()));

        getAs("/api/v1/groups/" + groupId + "/members", candidate)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(leader.getId().toString()));
    }

    @Test
    @DisplayName("Issue #11: Recruiting profile supports strengths and weaknesses")
    void recruitingProfileSupportsStrengthsAndWeaknesses() throws Exception {
        User student = user("profile-candidate", Role.STUDENT);

        mockMvc.perform(put("/api/v1/me/profile")
                        .header("Authorization", bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\":\"Passionate coder\",\"skills\":\"Java, React\",\"strengths\":\"Fast learner\",\"weaknesses\":\"Public speaking\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("Passionate coder"))
                .andExpect(jsonPath("$.skills").value("Java, React"))
                .andExpect(jsonPath("$.strengths").value("Fast learner"))
                .andExpect(jsonPath("$.weaknesses").value("Public speaking"));

        getAs("/api/v1/me/profile", student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strengths").value("Fast learner"))
                .andExpect(jsonPath("$.weaknesses").value("Public speaking"));
    }

    @Test
    @DisplayName("Issue #5: Reject leave request stores and returns rejection reason")
    void rejectLeaveRequestStoresAndReturnsReason() throws Exception {
        User leader = user("leave-lead", Role.STUDENT);
        String groupJson = body(postJson("/api/v1/groups", leader, Map.of("semester", "S-" + suffix))
                .andExpect(status().isCreated())).toString();
        UUID groupId = UUID.fromString(objectMapper.readTree(groupJson).get("id").asText());

        User member = user("leave-member", Role.STUDENT);
        UUID inviteId = UUID.fromString(body(postJson("/api/v1/groups/" + groupId + "/invites", leader,
                Map.of("email", member.getEmail())).andExpect(status().isCreated())).get("id").asText());
        postJson("/api/v1/invites/" + inviteId + "/accept", member, Map.of()).andExpect(status().isCreated());

        String leaveReqJson = body(postJson("/api/v1/groups/" + groupId + "/leave-requests", member,
                Map.of("reason", "Em bận việc gia đình")).andExpect(status().isCreated())).toString();
        UUID leaveId = UUID.fromString(objectMapper.readTree(leaveReqJson).get("id").asText());

        // Leader rejects with reason
        postJson("/api/v1/leave-requests/" + leaveId + "/reject", leader,
                Map.of("reason", "Nhóm đang cận kề deadline nộp đồ án"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectReason").value("Nhóm đang cận kề deadline nộp đồ án"));
    }
}
