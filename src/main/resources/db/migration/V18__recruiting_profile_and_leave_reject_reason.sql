-- Reason for rejecting a leave request
ALTER TABLE member_leave_requests ADD COLUMN reject_reason VARCHAR(1000);

-- Strengths and weaknesses for student recruiting profile (YC22)
ALTER TABLE users ADD COLUMN strengths VARCHAR(500);
ALTER TABLE users ADD COLUMN weaknesses VARCHAR(500);
