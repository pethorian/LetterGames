-- Sem informar id: a identity gera 1..10 e segue do 11 nos próximos inserts.
INSERT INTO jogos (titulo, plataforma, genero, duracao_media_horas, atualizado_em) VALUES
    ('Hollow Knight', 'PC', 'Metroidvania', 27, CURRENT_TIMESTAMP),
    ('The Witcher 3', 'PC', 'RPG', 103, CURRENT_TIMESTAMP),
    ('Celeste', 'Switch', 'Plataforma', 12, CURRENT_TIMESTAMP),
    ('Elden Ring', 'PS5', 'Soulslike', 98, CURRENT_TIMESTAMP),
    ('Hades', 'PC', 'Roguelike', 43, CURRENT_TIMESTAMP),
    ('Stardew Valley', 'PC', 'Simulação', 95, CURRENT_TIMESTAMP),
    ('God of War Ragnarök', 'PS5', 'Ação', 51, CURRENT_TIMESTAMP),
    ('Zelda: Breath of the Wild', 'Switch', 'Aventura', 98, CURRENT_TIMESTAMP),
    ('Baldur''s Gate 3', 'PC', 'RPG', 117, CURRENT_TIMESTAMP),
    ('Portal 2', 'PC', 'Puzzle', 14, CURRENT_TIMESTAMP);
