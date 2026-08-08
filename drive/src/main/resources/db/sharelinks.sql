CREATE TABLE shared_links (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    uuid CHAR(36) NOT NULL UNIQUE,

    file_id BIGINT NOT NULL,

    token VARCHAR(64) NOT NULL UNIQUE,

    password VARCHAR(255),

    expires_at DATETIME,

    download_limit INT DEFAULT NULL,

    download_count INT DEFAULT 0,

    is_active BOOLEAN DEFAULT TRUE,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_shared_file
        FOREIGN KEY (file_id)
        REFERENCES files(id)
        ON DELETE CASCADE
);