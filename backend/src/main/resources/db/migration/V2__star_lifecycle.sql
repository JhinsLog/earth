-- 별(이벤트)은 생성 후 일정 시간이 지나면 자동으로 사라진다(현재 30분, 임시 정책).
-- 스케줄러 주기와 무관하게 정확한 시점에 사라져야 하므로 만료 시각을 컬럼으로 둔다.
-- 조회 쿼리가 expires_at > now() 로 직접 거르고, 스케줄러는 상태 전환과
-- 실시간 전파만 담당한다.
ALTER TABLE events ADD COLUMN expires_at TIMESTAMPTZ;

UPDATE events SET expires_at = created_at + INTERVAL '30 minutes' WHERE expires_at IS NULL;

ALTER TABLE events ALTER COLUMN expires_at SET NOT NULL;

-- 수정 시각. 별 수정 기능에서 갱신된다.
ALTER TABLE events ADD COLUMN updated_at TIMESTAMPTZ;

-- 만료 대상만 훑으면 되므로 부분 인덱스로 충분하다.
CREATE INDEX idx_events_expires_active ON events (expires_at) WHERE status = 'ACTIVE';
