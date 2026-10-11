package com.capstone.tracking.group.dto;

import com.capstone.tracking.group.GroupMember;
import com.capstone.tracking.group.MemberStatus;

import java.time.Instant;
import java.util.UUID;

public record GroupMemberResponse(
        UUID id,
        UUID userId,
        String userFullName,
        String userEmail,
        boolean isLeader,
        Instant joinedAt,
        MemberStatus status,
        String studentCode,
        boolean eligible,
        String ineligibleReason
) {
    public static GroupMemberResponse from(GroupMember m) {
        String studentCode = m.getUser().getStudentCode();
        if (studentCode == null || studentCode.isBlank()) {
            String email = m.getUser().getEmail();
            if (email != null && email.contains("@")) {
                studentCode = email.substring(0, email.indexOf('@'));
            }
        }
        return new GroupMemberResponse(m.getId(), m.getUser().getId(), m.getUser().getFullName(),
                m.getUser().getEmail(), m.isLeader(), m.getJoinedAt(), m.getStatus(), studentCode,
                m.getUser().isEligible(), m.getUser().getIneligibleReason());
    }

    public GroupMemberResponse(UUID id, UUID userId, String userFullName, String userEmail, boolean isLeader,
                               Instant joinedAt, MemberStatus status) {
        this(id, userId, userFullName, userEmail, isLeader, joinedAt, status, null, true, null);
    }

    public GroupMemberResponse(UUID id, UUID userId, String userFullName, String userEmail, boolean isLeader,
                               Instant joinedAt, MemberStatus status, String studentCode) {
        this(id, userId, userFullName, userEmail, isLeader, joinedAt, status, studentCode, true, null);
    }
}
