-- Versioned AES-GCM ciphertext (nonce + authentication tag + key id) exceeds legacy 128 chars.
-- No automatic SQL transformation: encryption requires application-managed keys and AAD.
ALTER TABLE users ALTER COLUMN mfa_secret TYPE varchar(512);
