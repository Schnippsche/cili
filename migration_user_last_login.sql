-- migration_user_last_login.sql
-- Neue Spalte: users.last_login_at — Zeitpunkt (Serverzeit) des letzten erfolgreichen Logins.
-- Wird von AuthService.login() bei jedem echten Login gesetzt (nicht beim Token-Refresh).
-- Bestandsnutzer erhalten NULL; das Feld bleibt in der Benutzerverwaltung leer, bis sie sich
-- das erste Mal nach dem Deployment anmelden.
--
-- Gefahrlos einmalig ausfuehren. Bei erneutem Lauf meldet MySQL "Duplicate column" — dann
-- ist die Migration bereits erfolgt. Pruefen mit:
--   SHOW COLUMNS FROM users LIKE 'last_login_at';

ALTER TABLE `users`
    ADD COLUMN `last_login_at` datetime(6) DEFAULT NULL AFTER `role`;
