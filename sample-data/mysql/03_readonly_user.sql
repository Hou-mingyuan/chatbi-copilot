-- Demo query account: database-enforced SELECT-only access.
CREATE USER IF NOT EXISTS 'chatbi_ro'@'%' IDENTIFIED BY 'ChatBI!Readonly123';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'chatbi_ro'@'%';
GRANT SELECT, SHOW VIEW ON chatbi_demo.* TO 'chatbi_ro'@'%';
FLUSH PRIVILEGES;
