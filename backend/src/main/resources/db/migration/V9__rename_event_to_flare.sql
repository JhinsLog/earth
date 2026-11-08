-- 도메인 용어를 '이벤트'(화면에서는 '별')에서 '플레어'로 바꾼다. 화면·코드·API·스키마가 같은 이름을 쓰게 하려는 것이다.
-- 데이터는 그대로 두고 이름만 바꾼다. RENAME은 테이블을 다시 쓰지 않아 운영 중에도 순간적인 잠금만 잡는다.
--
-- 테이블·컬럼은 엔티티(@Table, @JoinColumn)와 맞아야 ddl-auto: validate를 통과하므로 반드시 바꾼다.
-- 인덱스·제약·시퀀스 이름은 검증 대상이 아니지만, 옛 이름이 남으면 실행 계획(EXPLAIN)이나 제약 위반 로그를 읽을 때
-- 테이블 이름과 어긋나 헷갈린다. 다만 이 이름들은 PostgreSQL이 자동으로 지은 것이라 환경마다 다를 수 있어,
-- 있을 때만 바꾼다. 없는 이름 하나 때문에 마이그레이션이 실패하면 기동 자체가 막히기 때문이다.

ALTER TABLE events RENAME TO flares;
ALTER TABLE event_confirmations RENAME TO flare_confirmations;

ALTER TABLE flare_confirmations RENAME COLUMN event_id TO flare_id;
ALTER TABLE chat_messages RENAME COLUMN event_id TO flare_id;
ALTER TABLE notifications RENAME COLUMN event_id TO flare_id;

-- 직접 만든 인덱스(V2, V3, V6, V8, V1의 채팅 인덱스).
ALTER INDEX IF EXISTS idx_events_expires_active RENAME TO idx_flares_expires_active;
ALTER INDEX IF EXISTS idx_events_active_created RENAME TO idx_flares_active_created;
ALTER INDEX IF EXISTS idx_events_active_lat_lng RENAME TO idx_flares_active_lat_lng;
ALTER INDEX IF EXISTS uq_events_source_external_id RENAME TO uq_flares_source_external_id;
ALTER INDEX IF EXISTS idx_event_confirmations_event RENAME TO idx_flare_confirmations_flare;
ALTER INDEX IF EXISTS idx_chat_messages_event RENAME TO idx_chat_messages_flare;

-- 기본키·유니크 제약은 인덱스 이름을 바꾸면 제약 이름도 함께 바뀐다.
ALTER INDEX IF EXISTS events_pkey RENAME TO flares_pkey;
ALTER INDEX IF EXISTS event_confirmations_pkey RENAME TO flare_confirmations_pkey;
ALTER INDEX IF EXISTS event_confirmations_event_id_user_id_key RENAME TO flare_confirmations_flare_id_user_id_key;

-- BIGSERIAL이 만든 시퀀스. 컬럼 기본값은 시퀀스 OID를 가리키므로 이름을 바꿔도 채번은 그대로 이어진다.
ALTER SEQUENCE IF EXISTS events_id_seq RENAME TO flares_id_seq;
ALTER SEQUENCE IF EXISTS event_confirmations_id_seq RENAME TO flare_confirmations_id_seq;

-- 외래키는 ALTER TABLE ... RENAME CONSTRAINT에 IF EXISTS가 없어 존재를 확인한 뒤 바꾼다.
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT * FROM (VALUES
            ('flares',              'events_author_id_fkey',              'flares_author_id_fkey'),
            ('flare_confirmations', 'event_confirmations_event_id_fkey',  'flare_confirmations_flare_id_fkey'),
            ('flare_confirmations', 'event_confirmations_user_id_fkey',   'flare_confirmations_user_id_fkey'),
            ('chat_messages',       'chat_messages_event_id_fkey',        'chat_messages_flare_id_fkey'),
            ('notifications',       'notifications_event_id_fkey',        'notifications_flare_id_fkey')
        ) AS t(tbl, old_name, new_name)
    LOOP
        IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = r.old_name) THEN
            EXECUTE format('ALTER TABLE %I RENAME CONSTRAINT %I TO %I', r.tbl, r.old_name, r.new_name);
        END IF;
    END LOOP;
END
$$;

COMMENT ON COLUMN flare_confirmations.granted_seconds IS
    '이 공감이 실제로 늘려준 수명(초). 취소 시 이만큼만 되돌린다. 0은 기록 이전에 생긴 공감.';
