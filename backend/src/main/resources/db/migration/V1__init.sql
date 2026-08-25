CREATE TABLE users
(
    id                BIGSERIAL PRIMARY KEY,
    provider          VARCHAR(20)  NOT NULL DEFAULT 'GOOGLE',
    provider_id       VARCHAR(100) NOT NULL,
    email             VARCHAR(255) NOT NULL,
    nickname          VARCHAR(50)  NOT NULL,
    profile_image_url VARCHAR(500),
    role              VARCHAR(20)  NOT NULL DEFAULT 'USER',
    level             INT          NOT NULL DEFAULT 1,
    exp               INT          NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_id)
);

CREATE TABLE events
(
    id          BIGSERIAL PRIMARY KEY,
    author_id   BIGINT       NOT NULL REFERENCES users (id),
    title       VARCHAR(80)  NOT NULL,
    content     VARCHAR(1000),
    category    VARCHAR(20)  NOT NULL DEFAULT 'ETC',
    latitude    DOUBLE PRECISION NOT NULL,
    longitude   DOUBLE PRECISION NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_events_lat_lng ON events (latitude, longitude);
CREATE INDEX idx_events_status ON events (status);

CREATE TABLE region_subscriptions
(
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id),
    label       VARCHAR(50)  NOT NULL,
    latitude    DOUBLE PRECISION NOT NULL,
    longitude   DOUBLE PRECISION NOT NULL,
    radius_km   DOUBLE PRECISION NOT NULL DEFAULT 5,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (user_id, label)
);

CREATE TABLE notifications
(
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id),
    event_id    BIGINT       NOT NULL REFERENCES events (id),
    message     VARCHAR(200) NOT NULL,
    is_read     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_user_unread ON notifications (user_id, is_read);

CREATE TABLE chat_messages
(
    id          BIGSERIAL PRIMARY KEY,
    event_id    BIGINT       NOT NULL REFERENCES events (id),
    user_id     BIGINT       NOT NULL REFERENCES users (id),
    content     VARCHAR(500) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_messages_event ON chat_messages (event_id, created_at);
