CREATE TABLE hero_identity(
  id bigint NOT NULL PRIMARY KEY AUTO_INCREMENT,
  hero LONGBLOB NOT NULL,
  encrypted_data LONGBLOB NULL
);