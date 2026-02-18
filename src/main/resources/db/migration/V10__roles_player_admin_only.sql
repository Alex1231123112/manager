-- Оставляем только роли Игрок и Админ: бывших капитанов делаем админами
UPDATE team_members SET role = 'ADMIN' WHERE role = 'CAPTAIN';
