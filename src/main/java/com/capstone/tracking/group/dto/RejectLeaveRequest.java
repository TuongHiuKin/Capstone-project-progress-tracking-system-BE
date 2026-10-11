package com.capstone.tracking.group.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;

/** Reason why the Leader or Admin turns down a member's leave request. */
public record RejectLeaveRequest(
        @JsonAlias({"rejectReason", "rejectionReason", "note", "message"})
        @Size(max = 1000) String reason
) {
}
